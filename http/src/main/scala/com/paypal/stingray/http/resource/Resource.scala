package com.paypal.stingray.http.resource

import scalaz._
import Scalaz._
import net.liftweb.json.scalaz.JsonScalaz._
import net.liftweb.json._
import com.paypal.stingray.common.json.JSONUtil._
import com.paypal.stingray.common.validation._
import spray.http._
import spray.http.HttpEntity._
import spray.http.HttpMethods._
import spray.http.HttpResponse
import StatusCodes._
import com.paypal.stingray.common.logging.LoggingSugar
import scala.concurrent._
import org.slf4j.LoggerFactory

/**
 *
 * @tparam ParsedRequest A representation of the request as this resource sees it. This should contain all the data from the request
 *                       needed by this resource to produce the response (except the body). Use the type HttpRequest and mix in
 *                       NoParsing to skip parsing
 * @tparam AuthInfo a structure for information gained during authorization. Use the type NoAuth and mixin AlwaysAuthorized to skip
 *                  authorization
 * @tparam PostBody the class to serialize the POST body to. Use the type NoBody if the resource doesn't do POST, or doesn't use a body
 * @tparam PutBody the class to serialize the PUT body to. Use the type NoBody if the resource doesn't do PUT, or doesn't use a body
 */
abstract class Resource[ParsedRequest, AuthInfo, PostBody, PutBody] extends LoggingSugar {

  protected lazy val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * the context used by default by futures created in this resource
   */
  lazy val executionContext = new ExecutionContext {
    def reportFailure(t: Throwable) {
      logger.warn(t.getMessage, t)
    }

    def execute(runnable: Runnable) {
      runnable.run()
    }
  }

  implicit lazy val context = executionContext

  /**
   * whether this service is available
   * @return true if available, false otherwise
   */
  def available: Boolean = true

  /**
   * the HTTP methods to accept in this server
   * @return a list of the allowed HTTP methods
   */
  def supportedHttpMethods: List[HttpMethod] = List(GET, PUT, POST, DELETE, HEAD)

  /**
   * attempt to parse the incoming request. if this is CPU intensive, you should do it in a background thread,
   * actor running on a dedicated thread, etc
   * @param r the request to parse into some data structure
   * @return either a failure response or the parsed request
   */
  def parseRequest(r: HttpRequest, pathParts: Map[String, String]): Future[ParsedRequest]

  /**
   * Convert the raw body into the PostBody type
   * @param r the http request
   * @return
   */
  def parsePostBody(r: HttpRequest): Future[Option[PostBody]] = none[PostBody].continue

  /**
   * Convert the raw body into the PutBody type
   * @param r the http request
   * @return
   */
  def parsePutBody(r: HttpRequest): Future[Option[PutBody]] = none[PutBody].continue

  /**
   * The message to be sent back with the WWW-Authenticate header when the request is
   * unauthorized. This particular form is important because android is stupid
   * @return the message
   */
  def unauthorizedChallenge(req: HttpRequest): List[HttpChallenge] = List(HttpChallenge("OAuth", req.uri.authority.host.toString))

  /**
   * determine if a given request is authorized to execute
   * @param p the parsed request
   * @return either a Failure(halt) or the auth result
   */
  def isAuthorized(p: ParsedRequest): Future[Option[AuthInfo]]

  /**
   * determine if an incoming request is forbidden to execute
   * @param p the parsed request
   * @return either a Failure(halt) or the auth result
   */
  def isForbidden(p: ParsedRequest): Future[Boolean] = false.continue
  def isForbidden(p: ParsedRequest, auth: AuthInfo): Future[Boolean] = isForbidden(p)

  /**
   * a list of content types that that this server can accept. These will be matched against
   * the Content-Type header of incoming requests.
   * @return a list of content types
   */
  lazy val acceptableContentTypes: List[ContentType] = List(ContentTypes.`application/json`)

  /**
   * the content type that this server provides
   * @return a list of content types
   */
  lazy val responseContentType: ContentType = ContentTypes.`application/json`

  /**
   * handle a GET request.
   * @return a response for the given request
   */
  def doGet(req: ParsedRequest): Future[HttpResponse] = HttpResponse(InternalServerError).continue
  def doGet(req: ParsedRequest, authInfo: AuthInfo): Future[HttpResponse] = doGet(req)

  /**
   * handle a HEAD request. By default it routes to get and strips any body
   * @return a response for the given request
   */
  def doHead(req: ParsedRequest): Future[HttpResponse] = doGet(req).map { resp =>
    resp.withEntity(Empty)
  }
  def doHead(req: ParsedRequest, authInfo: AuthInfo): Future[HttpResponse] = doGet(req)

  /**
   * handle a DELETE request
   * @return the response for the delete
   */
  def doDelete(req: ParsedRequest): Future[HttpResponse] = HttpResponse(InternalServerError).continue
  def doDelete(req: ParsedRequest, authInfo: AuthInfo): Future[HttpResponse] = doDelete(req)

  /**
   * handle a POST request. Use either this or doPostAsCreate
   * @return the response for the post
   */
  def doPost(req: ParsedRequest, body: PostBody): Future[HttpResponse] = HttpResponse(InternalServerError).continue
  def doPost(req: ParsedRequest, authInfo: AuthInfo, body: PostBody): Future[HttpResponse] = doPost(req, body)

  /**
   * handle a POST request, treating it as a create. The path of the newly created resource should be returned
   * along with the response, if applicable, and it will be incorporated into the location header if a 201 is
   * returned. Use either this or doPost
   * @return the response for the post and the new location
   */
  def doPostAsCreate(req: ParsedRequest, authInfo: AuthInfo, body: PostBody): Future[(HttpResponse, Option[String])] = {
    doPost(req, authInfo, body).map { resp =>
      resp -> none[String]
    }
  }

  /**
   * handle a PUT request
   * @return the response for the put
   */
  def doPut(req: ParsedRequest, b: PutBody): Future[HttpResponse] = HttpResponse(InternalServerError).continue
  def doPut(req: ParsedRequest, authInfo: AuthInfo, b: PutBody): Future[HttpResponse] = doPut(req, b)

  /**
   * handle an OPTIONS request
   * @return the response
   */
  def doOptions(req: ParsedRequest): Future[HttpResponse] = HttpResponse(InternalServerError).continue
  def doOptions(req: ParsedRequest, authInfo: AuthInfo): Future[HttpResponse] = doOptions(req)

  /**
   * Utility method to deserialize a json body via lift-json-scalaz
   * @param r the request to parse the body from
   * @tparam T the type to deserialize
   * @return
   */
  def fromJsonBody[T: JSONR](r: HttpRequest): Future[Option[T]] = {
    r.entity match {
      case body @ NonEmpty(_, _) => {
        for {
          json <- validating(parse(body.asString(body.contentType.definedCharset | HttpCharsets.`UTF-8`))).toOption.orHaltWith(BadRequest, "Could not parse json body")
          t <- fromJSON[T](json).leftMap { e =>
            logger.warn(s"Invalid json: ${e.list.toString()}")
            e
          }.toOption.orHaltWith(BadRequest, "Bad json in body")
        } yield t.some
      }
      case Empty => {
        fromJSON[T](JNull).toOption.orHaltWith(BadRequest, "missing body").map(_.some)
      }
    }
  }

  /**
   * Convenience method to return an exception as a 500 Internal Error with the body being the message
   * of the exception
   */
  def errorResponse(e: Exception): HttpResponse = {
    HttpResponse(InternalServerError, e.getMessage)
  }

  /**
   * Utility method to return HttpResponse with status OK and a serialized json body via lift-json-scalaz.
   * If the JSON processing is CPU intensive, it should be done in a background thread, dedicated actor, etc...
   * into an http body with the content type set
   * @param t the object to serialize
   * @tparam T the type to serialize from
   * @return
   */
  def jsonOKResponse[T: JSONW](t: T): HttpResponse = {
    HttpResponse(OK, toJsonBody(t))
  }

  /**
   * Utility method to serialize a json body via lift-json-scalaz.
   * If the JSON processing is CPU intensive, it should be done in a background thread, dedicated actor, etc...
   * into an http body with the content type set
   * @param t the object to serialize
   * @tparam T the type to serialize from
   * @return
   */
  def toJsonBody[T: JSONW](t: T): HttpEntity = {
    HttpEntity(responseContentType, toJSON(t).toWireFormat)
  }

  /**
   * Used under the covers to force simple error strings into the proper format
   * by default json objects
   * @param body the body
   * @return
   */
  def coerceError(body: Array[Byte]): HttpEntity = {
    toJsonBody(Map("errors" -> List(new String(body, "UTF-8"))))
  }
}
