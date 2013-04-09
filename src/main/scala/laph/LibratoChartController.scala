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
          chartId <- request.routeParams.get("chartId").map(_.toLong)
        } yield {
          val instrumentFuture = Librato(u, p).instrument(chartId)
          val chartFuture      = LibratoChart.createChart(LibratoChartView(u, p, chartId))

          for {
            instrument <- instrumentFuture
            chart      <- chartFuture
          } yield {
            render.body(chart)
              .header("Content-Type", "image/png")
              .header("Chart-Name", instrument.name)
          }
        }).getOrElse(render.status(BAD_REQUEST).toFuture)
      case _ => render.status(UNAUTHORIZED).toFuture
    }
  }

}