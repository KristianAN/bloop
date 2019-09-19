package bloop.io

import bloop.logging.RecordingLogger
import scala.concurrent.Promise
import bloop.util.TestUtil
import java.nio.file.Files
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import bloop.util.TestProject

object SourceHasherSpec extends bloop.testing.BaseSuite {
  test("cancellation works") {
    val largeFileContents = {
      val sb = new StringBuilder()
      var base = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      for (i <- 0 to 1000) {
        sb.++=(base)
      }
      sb.result()
    }

    TestUtil.withinWorkspace { workspace =>
      import bloop.engine.ExecutionContext.ioScheduler
      val logger = new RecordingLogger()
      val cancelPromise = Promise[Unit]()
      val sources = {
        var allSources: List[String] = Nil
        for (i <- 0 to 200) {
          val uniqueFile = workspace.resolve(s"/A$i.scala")
          val contents = s"${uniqueFile}${System.lineSeparator}$largeFileContents"
          allSources = contents :: allSources
        }
        allSources
      }

      val `A` = TestProject(workspace, "a", sources)
      val state = loadState(workspace, List(`A`), logger)
      val projectA = state.getProjectFor(`A`)

      val sourceHashesTask =
        SourceHasher.findAndHashSourcesInProject(projectA, 2, cancelPromise, ioScheduler)
      val running = sourceHashesTask.runAsync(ioScheduler)

      Thread.sleep(10)
      running.cancel()

      val result = Await.result(running, FiniteDuration(20, "s"))
      assert(result.isLeft)
      assert(cancelPromise.isCompleted)
    }
  }
}
