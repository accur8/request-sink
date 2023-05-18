package io.accur8.requestsink


import a8.shared.app.LoggingF
import zio.{Chunk, Layer, Task, ZIO}
import zio.http.{Body, Http, HttpApp, HttpError, Request, Response, Status}
import a8.shared.SharedImports._

object Router {
  type Env = Any
  type M[A] = zio.ZIO[Env,Throwable,A]
}

case class Router(
  placeholder: String,
  protocol: String = "http"
)
  extends LoggingF
{

  import Router._

  lazy val routes: HttpApp[Any, Nothing] =
    Http.collectZIO[Request] { request =>
      val context = s"${request.method} ${request.url.encode}"

      val rawEffect: M[Response] =
        for {
          // some dancing here since curl will consume the request body
          wrappedRequest <- curl(request, true)
          _ <- loggerF.debug(s"curl for request\n${wrappedRequest._1.indent("    ")}")
          responseEffect = SinkHandler.processRequest(wrappedRequest._1, wrappedRequest._2)
          response <-
            responseEffect
              .uninterruptible
              .either
              .flatMap {
                case Left(HttpResponseException(httpResponse)) =>
                  zsucceed(httpResponse)
                case Left(httpError: HttpError) =>
                  loggerF.warn(s"Error servicing request: ${context}, responding with ${httpError}") *>
                    HttpResponses.fromError(httpError)
                case Left(th) =>
                  loggerF.error(s"Error servicing request: ${context}", th) *>
                    HttpResponses.text(th.stackTraceAsString, status = Status.InternalServerError)
                case Right(s) =>
                  zsucceed(s)
              }
          _ <- loggerF.debug(s"completed processing ${response.status.code} -- ${context}")
        } yield response

      val effectWithoutErrors: ZIO[Env, Nothing, Response] =
        rawEffect
          .either
          .flatMap {
            case Left(th) =>
              // this shouldn't happen but we will turn this into a 500 error
              loggerF.warn(s"Cleaning up unexpected error: ${context}, responding with 500", th) *>
                HttpResponses.text(th.stackTraceAsString, status = Status.InternalServerError)
            case Right(response) =>
              zsucceed(response)
          }
          .correlateWith(context)

      effectWithoutErrors
        .scoped
//        .provide(
//          zl_succeed(s3Client),
//          zl_succeed(s3),
//          zl_succeed(config),
//          UserService.layer,
//          zl_succeed(resolvedModel),
//          zl_succeed(anonymousSubnetManager),
//        )

    }

  def curl(request: Request, logRequestBody: Boolean): Task[(String,Request)] = {
    if ( logRequestBody ) {
      curlForRequest(request)
    } else {
      curlForRequestNoBody(request)
    }
  }


  def curlForRequestNoBody(request: Request): Task[(String,Request)] = {

    val curl: String = {
      //          val requestBodyStr = new String(requestBodyByteBuf.array())
      val initialLines: Chunk[String] = Chunk("curl", s"-X ${request.method}")
      val headerLines: Chunk[String] = request.headers.map(h => s"-H '${h.headerName}: ${h.renderedValue}'").toChunk
      val url: Chunk[String] = Chunk(s"${config.protocol}://${request.rawHeader("Host").getOrElse("nohost")}${request.url.encode}")
      (initialLines ++ headerLines ++ url)
        .mkString(" \\\n    ")
    }

    ZIO.succeed(curl -> request)

  }

  def curlForRequest(request: Request): Task[(String,Request)] = {

    def impl(requestBodyStr: Option[String]): (String,Request) = {
      val curl: String = {
        //          val requestBodyStr = new String(requestBodyByteBuf.array())
        val initialLines: Chunk[String] = Chunk("curl", s"-X ${request.method}")
        val headerLines: Chunk[String] = request.headers.filterNot(_.headerName =:= "host").map(h => s"-H '${h.headerName}: ${h.renderedValue}'").toChunk
        val url: Chunk[String] = Chunk(s"'${protocol}://${request.rawHeader("Host").getOrElse("nohost")}${request.url.encode}'")
        val requestBody = Chunk.fromIterable(requestBodyStr.map(rbs => s"--data '${rbs}'"))
        (initialLines ++ headerLines ++ url ++ requestBody)
          .mkString(" \\\n    ")
      }

      val newData =
        requestBodyStr match {
          case Some(rbs) =>
            Body.fromString(rbs)
          case None =>
            Body.empty
        }

      curl -> request.copy(body = newData)

    }

    request
      .body
      .asString
      .map {
        case bodyStr if bodyStr.isEmpty =>
          impl(None)
        case bodyStr =>
          impl(bodyStr.some)
      }
  }

}
