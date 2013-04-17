package laph

import com.twitter.finatra._

object App {
  val app = new LibratoChartController
  val auth = new LibratoAuth

  def main(args: Array[String]) = {
    startLibratoPoller
    FinatraServer.addFilter(auth)
    FinatraServer.register(app)
    FinatraServer.start()
  }

  def startLibratoPoller: Unit = {
    val LibratoUser     = sys.env.get("LIBRATO_USER")
    val LibratoPassword = sys.env.get("LIBRATO_PASSWORD")
    for {
      libratoUser ← LibratoUser
      libratoPassword ← LibratoPassword
    } yield {
      LibratoData.poll(LibratoAccount(libratoUser, libratoPassword))
    }
  }
}