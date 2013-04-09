package laph

import com.twitter.finatra.Controller
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import laph.Templates.LibratoChartView

class LibratoChartController extends Controller {

  import HttpResponseStatus._

  implicit def statusCode2Int(s: HttpResponseStatus) = s.getCode

  get("/chart/:chartId.png") { request =>
    request.request match {
      case LibratoAuthRequest(u, p, r) =>
        (for {
          chartId <- request.routeParams.get("chartId")
        } yield {
          LibratoChart.createChart(LibratoChartView(u, p, chartId)).map { img =>
            render.body(img).header("Content-Type", "image/png")
          }
        }).getOrElse(render.status(BAD_REQUEST).toFuture)
      case _ => render.status(UNAUTHORIZED).toFuture
    }
  }

}