package laph

import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{RequestBuilder, Http}
import com.twitter.conversions.time._
import com.twitter.util.Base64StringEncoder.{ encode ⇒ base64encode }
import com.twitter.util.{Future, Duration}
import com.codahale.jerkson.{ Json ⇒ Jerkson }

case class LibratoJson()
case class LibratoQuery(offset: Int, length: Int, found: Int, total: Int)
case class LibratoMetrics(query: LibratoQuery, metrics: List[LibratoMetric])
case class LibratoMetric(name: String)
case class LibratoInstrument(id: Long, name: String)
case class LibratoInstrumentQuery(query: LibratoQuery, instruments: List[LibratoInstrument])

case class Librato(username: String, password: String) {
  import laph.ChannelBufferUtil._
  import LibratoClient.request
  private val auth = username → password

  def metrics: Future[LibratoMetrics] = request(auth, "/v1/metrics").map(asJson[LibratoMetrics])

  def instrument(chartId: Long): Future[LibratoInstrument] = {
    request(auth, "/v1/instruments/%d".format(chartId))
      .map(asJson[LibratoInstrument])
  }

  def allInstruments = {
    request(auth, "/v1/instruments?length=1")
      .map(asJson[LibratoInstrumentQuery])
      .map(_.query.total)
      .map(LibratoClient.slices)
      .flatMap { slices ⇒
        Future.collect(
          slices.map { case (offset, total) ⇒
            request(auth, "/v1/instruments?offset=%d&total=%d".format(offset, total))
              .map(asJson[LibratoInstrumentQuery])
              .map(_.instruments)
          }
        )
      }
      .map(_.flatten)
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

  val batch = 100
  def slices(total: Int): Seq[(Int, Int)] = {
    val batches = for (i <- 0 to (total / batch - 1)) yield (i * 100, batch)
    if (total % batch == 0) batches
    else batches :+ ((total / batch) * batch -> total % batch)
  }
}