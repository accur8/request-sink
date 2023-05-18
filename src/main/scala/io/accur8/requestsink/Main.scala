package io.accur8.requestsink


import a8.shared.ConfigMojo
import a8.shared.app.BootstrapConfig.UnifiedLogLevel
import a8.shared.app.BootstrappedIOApp.BootstrapEnv
import a8.shared.app.{BootstrappedIOApp, LoggingF}
import wvlet.log.LogLevel
import zio.http.HttpError.{BadRequest, InternalServerError}
import zio.http.Method.{GET, POST}
import zio.http.*
import zio.http.Server.RequestStreaming
import zio.{Chunk, Task, ZIO}

import java.net.InetAddress

object Main extends BootstrappedIOApp {

  type Env = Any

  val port = 7002
  val keepalive = true

  override def defaultLogLevel = UnifiedLogLevel(wvlet.log.LogLevel.TRACE)

  override def initialLogLevels: Iterable[(String, LogLevel)] =
    super.initialLogLevels ++
      Seq(
        "nodebuglogging",
        "org.xnio",
        "org.apache.http",
        "jdk.event",
        "com.amazonaws",
        "javax.xml.bind",
      ).map(_ -> LogLevel.INFO)


  override lazy val defaultAppName: String = "request-sink-server"

  override def runT: ZIO[BootstrapEnv, Throwable, Unit] = {
    val router = Router("")
    val effect =
      for {
        _ <- loggerF.info(s"http server is listening on port ${port}")
        _ <-
          Server
            .serve(router.routes)
            .provide(
              Server
                .defaultWith(config =>
                  config
                    .port(port)
                    .keepAlive(keepalive)
                    .withRequestStreaming(RequestStreaming.Enabled)
                )
            )
      } yield ()
    effect
  }

}
