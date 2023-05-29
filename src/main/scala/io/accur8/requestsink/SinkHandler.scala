package io.accur8.requestsink


import zio.http.{Request, Response}
import Router.*
import a8.httpserver.HttpResponses
import a8.httpserver.model.ContentPath
import a8.shared.FileSystem
import a8.shared.SharedImports._

case class SinkHandler(
  dataDir: a8.shared.ZFileSystem.Directory,
) {

  def contentPath(request: Request): ContentPath =
    ContentPath(
      request.path.textSegments,
      false,
    )

  def processRequest(requestInfo: RequestInfo): M[Response] = {
    val cp = contentPath(requestInfo.wrappedRequest)
    val dir = dataDir.subdir(cp.fullPath)
    val uuid = java.util.UUID.randomUUID().toString.replace("-","").substring(0,10)
    val timestamp = FileSystem.fileSystemCompatibleTimestamp()
    val prefix = timestamp + "-" + uuid
    for {
      _ <- dir.resolve
      _ <- dir.file(prefix + ".curl").write(requestInfo.curl)
      _ <-
        requestInfo.requestBody match {
          case None =>
            zunit
          case Some(rb) =>
            dir.file(prefix + ".requestBody").write(rb)
        }
      response <- HttpResponses.Ok()
    } yield response
  }

}
