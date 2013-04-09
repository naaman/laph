package laph

import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{Response, RequestBuilder, Http}
import com.twitter.conversions.time._
import com.twitter.util.Base64StringEncoder.{ encode => base64encode }
import com.twitter.util.{Future, Duration}
import com.codahale.jerkson.{ Json => Jerkson }

case class LibratoJson()
case class LibratoMetrics(query: Any, metrics: List[LibratoMetric])
case class LibratoMetric(name: String)
case class LibratoInstrument(id: Long, name: String)

case class Librato(username: String, password: String) {
  import laph.ChannelBufferUtil._
  import LibratoClient.request
  private val auth = username -> password

  def metrics: Future[LibratoMetrics] = request(auth, "/v1/metrics").map(asJson[LibratoMetrics])

  def instrument(chartId: Long): Future[LibratoInstrument] = {
    request(auth, "/v1/instruments/%d".format(chartId))
      .map(asJson[LibratoInstrument])
  }
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
    client(
      RequestBuilder().url("https://" + libratoApiHostWithPort + path)
        .setHeader("Host", libratoApiHostWithPort)
        .setHeader(AUTHORIZATION, "Basic " + base64encode(auth._1 + ":" + auth._2))
        .buildGet()
    )
  }
}