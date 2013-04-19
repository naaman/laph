import librato.{LibratoAccount, LibratoData}
import play.api._

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    LibratoData.poll(
      LibratoAccount(
        app.configuration.getString("librato.user").get,
        app.configuration.getString("librato.password").get
      )
    )
  }
}