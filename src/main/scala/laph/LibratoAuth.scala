package laph

import com.twitter.finagle.{Service, http, SimpleFilter}
import java.util.regex.Pattern
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpVersion}
import com.twitter.util.Base64StringEncoder.decode

case class LibratoAuthRequest(username: String, password: String, request: http.Request)
  extends http.RequestProxy

class LibratoAuth extends SimpleFilter[http.Request, http.Response] {

  val LibratoUser      = sys.env.get("LIBRATO_USER")
  val LibratoPassword  = sys.env.get("LIBRATO_PASSWORD")
  val RequireAuth      = (for (u <- LibratoUser; p <- LibratoPassword) yield false).getOrElse(true)

  val BasicAuthPattern = Pattern.compile("\\s*basic\\s*", Pattern.CASE_INSENSITIVE)

  val Unauthorized: Future[http.Response] =
    Future.value(http.Response(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED))

  def stripBasic(auth: String) = BasicAuthPattern.matcher(auth).replaceFirst("").trim()

  def parseAuth(auth: String) = {
    new String(decode(stripBasic(auth))).split(":", 2).toList match {
      case username :: password :: Nil => Some((username, password))
      case _                           => None
    }
  }

  def apply(request: http.Request,
            service: Service[http.Request, http.Response]): Future[http.Response] = {
    if (RequireAuth) {
      (for {
        auth                 <- request.authorization
        (username, password) <- parseAuth(auth)
      } yield {
        val authReq = LibratoAuthRequest(username, password, request)
        service(authReq)
      }).getOrElse(Unauthorized)
    } else {
      service(LibratoAuthRequest(LibratoUser.get, LibratoPassword.get, request))
    }
  }

}