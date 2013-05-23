package util

trait ConfigModule {
  def config: Config
  trait Config {
    def getOpt(key: String): Option[String]
    def get(key: String): String
  }
}

trait PlayConfigModule extends ConfigModule {
  val config = PlayConfig
  object PlayConfig extends Config {
    import play.api.Play.current
    import play.api.Play.configuration

    def getOpt(key: String): Option[String] = configuration.getString(key)
    def get(key: String): String = {
      configuration.getString(key).getOrElse(sys.error(s"$key is not defined."))
    }
  }
}

trait MockConfigModule extends ConfigModule {
  def configMap: Map[String, String]
  def config = new MockConfig(configMap)
  class MockConfig(mockConfig: Map[String, String]) extends Config {
    def getOpt(key: String): Option[String] = mockConfig.get(key)
    def get(key: String): String = {
      mockConfig.get(key).getOrElse(sys.error(s"$key is not defined."))
    }
  }
}