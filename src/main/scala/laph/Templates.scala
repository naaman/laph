package laph

import com.twitter.finatra.View

object Templates {
  case class LibratoChartView(libratoUser: String,
                              libratoToken: String,
                              chartId: String) extends View {
    val template = "librato-chart"
  }
}
