package bloop.logging

import scribe.LogRecord

trait ScribeAdapter extends scribe.LoggerSupport[Unit] { self: Logger =>

  override def log(record: LogRecord): Unit = {
    import scribe.Level
    val msg = record.logOutput.plainText
    record.level match {
      case Level.Info => info(msg)
      case Level.Error => error(msg)
      case Level.Warn => warn(msg)
      case Level.Debug => debug(msg)(DebugFilter.Bsp)
      case Level.Trace =>
        val throwable = if (record.messages.filter(_.value.isInstanceOf[Throwable]).nonEmpty) {
          Some(record.messages.head.value.asInstanceOf[Throwable])
        } else None
        throwable match {
          case Some(t) => trace(t)
          case None => debug(msg)(DebugFilter.Bsp)
        }
      case _ =>
    }
  }
}
