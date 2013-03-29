package laph

import com.twitter.finatra._

object App {
  val app = new LibratoChartController
  val auth = new LibratoAuth

  def main(args: Array[String]) = {
    FinatraServer.addFilter(auth)
    FinatraServer.register(app)
    FinatraServer.start()
  }
}