package laph

import com.twitter.finatra.Controller
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import com.twitter.finagle.http.{Response, MediaType}
import org.jboss.netty.buffer.ChannelBuffers
import laph.S3.{S3Secret, S3Key}

class LibratoChartController extends Controller {

  implicit def statusCode2Int(s: HttpResponseStatus) = s.getCode

  val Status       = (s: HttpResponseStatus) ⇒ render.status(s).body("").toFuture
  val BadRequest   = Status(HttpResponseStatus.BAD_REQUEST)
  val Unauthorized = Status(HttpResponseStatus.UNAUTHORIZED)

  get("/chart/:chartId.png") { request ⇒
    request.request match {
      case LibratoAuthRequest(u, p, r) ⇒
        (for {
          chartId ← request.routeParams.get("chartId").map(_.toLong)
        } yield {
          LibratoChart.createChart(LibratoChartRequest(u, p, chartId)).map { chart ⇒
            render.body(chart).header("Content-Type", "image/png")
          }
        }).getOrElse(BadRequest)
      case _ ⇒ Unauthorized
    }
  }

  post("/chart") { request ⇒
    request.request match {
      case LibratoAuthRequest(u, p, r) ⇒
        (for {
          contentType ← request.contentType
          json        ← Option(JsonUtil.parse[ChartCreateRequest](request.contentString)) if contentType == MediaType.Json
        } yield {
          LibratoChart.createChartInS3(
            LibratoChartRequest(u, p, json.chartId)
          ).map(render.body(_))
        }).getOrElse(BadRequest)
      case _ ⇒ Unauthorized
    }
  }
}

case class ChartCreateRequest(chartId: Long)