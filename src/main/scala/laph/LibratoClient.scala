package laph

import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.Http
import com.twitter.conversions.time._
import com.twitter.util.Base64StringEncoder.{ encode => base64encode }
import com.twitter.util.Duration
import com.codahale.jerkson.{ Json => Jerkson }

case class LibratoJson()
case class LibratoMetrics(query: Any, metrics: List[LibratoMetric])
case class LibratoMetric(name: String)

case class Librato(username: String, password: String) {
  import laph.ChannelBufferUtil._
  private val auth = username -> password

  def metrics = LibratoClient.request(auth, "/v1/metrics").map(asJson[LibratoMetrics])
}

object LibratoClient {
  val libratoApiHost: String = "metrics-api.librato.com"
  val libratoApiHostWithPort: String = libratoApiHost + ":443"
  val tcpConnectTimeout: Duration = 60.seconds
  val hostConnectionLimit: Int = 50

  val client: Service[HttpRequest, HttpResponse] = ClientBuilder()
    .codec(Http())
    .hosts(libratoApiHostWithPort)
    .tls(libratoApiHost)
    .tcpConnectTimeout(tcpConnectTimeout)
    .hostConnectionLimit(hostConnectionLimit)
    .logger(java.util.logging.Logger.getLogger("http"))
    .build()

  implicit def string2bytearray(s: String) = s.getBytes("UTF-8")

  def request(auth: (String, String), path: String, method: HttpMethod = HttpMethod.GET) = {
    val req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, path)
    req.addHeader("Host", libratoApiHostWithPort)
    req.addHeader(AUTHORIZATION, "Basic " + base64encode(auth._1 + ":" + auth._2))
    client(req)
  }
}