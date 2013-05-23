package librato

import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WS
import com.ning.http.util.Base64
import ExecutionContext.Implicits.global
import play.api.libs.json.Json

case class LibratoJson()
case class LibratoQuery(offset: Int, length: Int, found: Int, total: Int)
case class LibratoMetrics(query: LibratoQuery, metrics: List[LibratoMetric])
case class LibratoMetric(name: String)
case class LibratoInstrument(id: Int, name: String)
case class LibratoInstrumentQuery(query: LibratoQuery, instruments: List[LibratoInstrument])

case class Librato(username: String, password: String) {
  import LibratoClient.request
  private val auth = username → password
  implicit val libratoQuery = Json.reads[LibratoQuery]
  implicit val libratoMetric = Json.reads[LibratoMetric]
  implicit val libratoMetrics = Json.reads[LibratoMetrics]
  implicit val libratoInstrument = Json.reads[LibratoInstrument]
  implicit val libratoInstrumentQuery = Json.reads[LibratoInstrumentQuery]

  def metrics: Future[LibratoMetrics] = request(auth, "/v1/metrics").map(_.json.as[LibratoMetrics])

  def instrument(chartId: Long): Future[LibratoInstrument] =
    request(auth, "/v1/instruments/%d".format(chartId))
      .map(_.json.as[LibratoInstrument])

  def allInstruments =
    request(auth, "/v1/instruments?length=1")
      .map(_.json.as[LibratoInstrumentQuery])
      .map(_.query.total)
      .map(LibratoClient.slices)
      .flatMap { slices ⇒
        Future.sequence(
          slices.map { case (offset, total) ⇒
            request(auth, "/v1/instruments?offset=%d&total=%d".format(offset, total))
              .map(_.json.as[LibratoInstrumentQuery].instruments)
          }
        )
      }.map(_.flatten)
}

trait LibratoClient {
  val libratoApiHost: String = "metrics-api.librato.com"
  val libratoApiUrl: String  = s"https://${libratoApiHost}"

  private def authHeader(auth: (String, String))  = AUTHORIZATION -> s"Basic ${encodedAuth(auth)}"
  private def encodedAuth(auth: (String, String)) = Base64.encode(s"${auth._1}:${auth._2}".getBytes)

  def request(auth: (String, String), path: String, method: HttpMethod = HttpMethod.GET) =
    WS.url(s"${libratoApiUrl}/${path}").withHeaders(authHeader(auth)).get

  val batch = 100
  def slices(total: Int): Seq[(Int, Int)] = {
    val batches = for (i <- 0 to (total / batch - 1)) yield (i * 100, batch)
    if (total % batch == 0) batches
    else batches :+ ((total / batch) * batch -> total % batch)
  }
}

object LibratoClient extends LibratoClient