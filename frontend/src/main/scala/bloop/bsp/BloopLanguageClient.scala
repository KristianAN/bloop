package bloop.bsp

import java.io.OutputStream

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

import bloop.task.Task

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.Endpoint
import monix.execution.Ack
import monix.execution.Callback
import monix.execution.Cancelable
import monix.execution.atomic.Atomic
import monix.execution.atomic.AtomicInt
import monix.reactive.Observer
import scribe.LoggerSupport
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage

// Interop stuff: TODO move somewhere
case class RawJson(
    value: Array[Byte]
)

sealed trait RpcResponse[T]

final case class RpcSuccess[T](
    value: T,
    underlying: ResponseMessage
) extends RpcResponse[T]

final case class RpcFailure[T](
    methodName: String,
    underlying: ResponseError
)

/**
 * A copy of `jsonrpc4s.RpcClient` that is uses `bloop.task.Task`
 * and has a difference in cancelRequest handling.
 * `jsonrpc4s` fails the ongoing request if it was cancelled which is not how it worked previously.
 */
class BloopLanguageClient(
    out: Observer[Message],
    logger: LoggerSupport[Unit]
) {

  type RequestId = Either[String, Number]

  protected val counter: AtomicInt = Atomic(1)
  protected val activeServerRequests = TrieMap.empty[RequestId, Callback[Throwable, Message]]

  protected val notificationsLock = new Object()
  protected def toJson[R: JsonValueCodec](r: R): RawJson = RawJson(writeToArray(r))

  def notify[A](
      endpoint: Endpoint,
      params: A
  ): Future[Ack] = notify(endpoint, Some(params), Map.empty)

  def notify[A](
      endpoint: Endpoint,
      params: Option[A]
  ): Future[Ack] = notify(endpoint, params, Map.empty)

  def notify[A](
      method: String,
      params: Option[A],
      headers: Map[
        String,
        String
      ] // LSP4J: NotificationMessage in lsp4jsonrpc does not have header field
  ): Future[Ack] = {
    val msg = new NotificationMessage()
    params.map(msg.setParams)
    msg.setMethod(method)

    // Send notifications in the order they are sent by the caller
    notificationsLock.synchronized {
      out.onNext(msg)
    }
  }

  def request[A, B](
      endpoint: Endpoint,
      params: A
  ): Task[RpcResponse[B]] = request(endpoint, Some(params), Map.empty)

  def request[A, B](
      endpoint: Endpoint,
      params: Option[A]
  ): Task[RpcResponse[B]] = request(endpoint, params, Map.empty)

  def request[A, B](
      endpoint: Endpoint,
      params: Option[A],
      headers: Map[String, String]
  ): Task[RpcResponse[B]] = {
    val reqId = RequestId(counter.incrementAndGet())
    val response = Task.create[Response] { (s, cb) =>
      val scheduled = s.scheduleOnce(Duration(0, "s")) {
        val json = Request(endpoint.method, params.map(toJson(_)), reqId, headers)
        activeServerRequests.put(reqId, cb)
        out.onNext(json)
        ()
      }

      Cancelable { () =>
        scheduled.cancel()
        this.notify(RpcActions.cancelRequest, CancelParams(reqId))
        ()
      }
    }

    response.map {
      // This case can never happen given that no response isn't a valid JSON-RPC message
      case Response.None => sys.error("Fatal error: obtained `Response.None`!")
      case err: Response.Error => RpcFailure(endpoint.method, err)
      case suc: Response.Success =>
        Try(readFromArray[B](suc.result.value)).toEither match {
          case Right(value) => RpcSuccess(value, suc)
          case Left(err) =>
            RpcFailure(endpoint.method, Response.invalidParams(err.toString, reqId))
        }
    }
  }

  def clientRespond(response: Response): Unit = {
    for {
      id <- response match {
        case Response.None => Some(RequestId.Null)
        case Response.Success(_, requestId, _, _) => Some(requestId)
        case Response.Error(_, requestId, _, _) => Some(requestId)
      }
      callback <- activeServerRequests.remove(id).orElse {
        logger.error(s"Response to unknown request: $response")
        None
      }
    } {
      callback.onSuccess(response)
    }
  }

  def serverRespond(response: Response): Future[Ack] = {
    response match {
      case Response.None => Ack.Continue
      case x: Response.Success => out.onNext(x)
      case x: Response.Error => out.onNext(x)
    }
  }
}

object BloopLanguageClient {

  def fromOutputStream(out: OutputStream, logger: LoggerSupport): BloopLanguageClient = {
    val msgOut = Message.messagesToOutput(Left(out), logger)
    new BloopLanguageClient(msgOut, logger)
  }
}
