package laph

import com.twitter.finatra.Controller
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.apache.commons.io.IOUtils
import java.io.FileInputStream
import laph.Templates.LibratoChartView

class LibratoChartController extends Controller {

  import HttpResponseStatus._

  implicit def statusCode2Int(s: HttpResponseStatus) = s.getCode

  get("/:chartId.png") { request =>
    request.request match {
      case LibratoAuthRequest(u, p, r) =>
        (for {
          chartId <- request.routeParams.get("chartId")
        } yield {
          val png = LibratoChart.createChart(LibratoChartView(u, p, chartId))
          val img = IOUtils.toByteArray(new FileInputStream(png))
          render.body(img).header("Content-Type", "image/png").toFuture
        }).getOrElse(render.status(BAD_REQUEST).toFuture)
      case _ => render.status(UNAUTHORIZED).toFuture
    }
  }

}