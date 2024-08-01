package bloop.logging

import java.io.File

import ch.epfl.scala.bsp4j

import bloop.io.AbsolutePath
import bloop.reporter.Problem

sealed abstract class CompilationEvent

object CompilationEvent {

  /**
   * Defines all information required to report a compile start notification to
   * a client.
   *
   * The compile start notification must always have a corresponding compile
   * end notification, defined by [[EndCompilation]].
   *
   * @param projectName The project name, useful when enriching messages to the client.
   * @param projectUri The URI uniquely representing the project to the BSP client.
   * @param msg The message summarizing the triggered incremental compilation cycle.
   * @param taskId The task id to use for this publication.
   */
  case class StartCompilation(
      projectName: String,
      projectUri: String,
      msg: String,
      taskId: bsp4j.TaskId
  ) extends CompilationEvent

  /**
   * Defines all information required to report a compile progress notification
   * to a client.
   *
   * The compile progress notification must always the same task id than
   * [[StartCompilation]] and [[EndCompilation]].
   *
   * @param projectName The project name, useful when enriching messages to the client.
   * @param projectUri The URI uniquely representing the project to the BSP client.
   * @param taskId The task id to use for this publication.
   * @param problems The sequence of problems that were found during compilation.
   * @param code The status code associated with the finished compilation event.
   */
  case class ProgressCompilation(
      projectName: String,
      projectUri: String,
      taskId: bsp4j.TaskId,
      progress: Long,
      total: Long,
      percentage: Long
  ) extends CompilationEvent

  /**
   * Defines all information required to report a compile end notification to a
   * client.
   *
   * The compile end notification must always the same task id than its
   * counterpart, defined by [[StartCompilation]].
   *
   * @param projectName The project name, useful when enriching messages to the client.
   * @param projectUri The URI uniquely representing the project to the BSP client.
   * @param taskId The task id to use for this publication.
   * @param problems The sequence of problems that were found during compilation.
   * @param code The status code associated with the finished compilation event.
   * @param isNoOp Whether the compilation was a no-op or not.
   * @param isLastCycle Whether the user can expect this to be the last `taskFinish` notification.
   * @param clientDir The client directory of the compilation end when this
   *   client directory is fully populated and ready to be read for the client.
   *   This value is only present if this compilation end is the last cycle and
   *   the status code is OK.
   * @param analysisOut The analysis file attached to the compilation end when
   *   the analysis file is ready to be read for the client. This value is only
   *   present if this compilation end is the last cycle and the status code is OK.
   */
  case class EndCompilation(
      projectName: String,
      projectUri: String,
      taskId: bsp4j.TaskId,
      problems: Seq[Problem],
      code: bsp4j.StatusCode,
      isNoOp: Boolean,
      isLastCycle: Boolean,
      clientDir: Option[AbsolutePath],
      analysisOut: Option[AbsolutePath]
  ) extends CompilationEvent

  /**
   * Defines all information required to report a diagnostic to a client.
   *
   * @param projectUri The URI uniquely representing the project to the BSP client.
   * @param problem The problem we're reporting to the client.
   * @param clear Whether we should clear or not diagnostics in the client for
   *              the problem URI.
   */
  case class Diagnostic(
      projectUri: String,
      problem: xsbti.Problem,
      clear: Boolean
  ) extends CompilationEvent

  /**
   * Defines all information required to report a no diagnostic to a client.
   *
   * @param projectUri The URI uniquely representing the project to the BSP client.
   * @param file The file we're cleaning all diagnostics at.
   */
  case class NoDiagnostic(
      projectUri: String,
      file: File
  ) extends CompilationEvent
}
