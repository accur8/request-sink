package io.accur8.requestsink


import zio.http.{Request, Response}
import Router.*

object SinkHandler {


  def processRequest(curl: String, request: Request): M[Response] =
    ???

}
