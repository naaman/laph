package librato

import java.io.{FileWriter, File}
import org.jboss.netty.handler.codec.http._
import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import play.api.libs.ws.WS
import play.api.libs.json.Json
import scala.concurrent.{Await, Future}
import com.ning.http.util.Base64
import fly.play.s3.{PUBLIC_READ, BucketFile, S3}
import fly.play.aws.auth.AwsCredentials
import util.{PlayConfigModule, ConfigModule}

case class LibratoChartRequest(
  username:   String,
  password:   String,
  id:         Int,
  name:       String,
  duration:   Long,
  uploadToS3: Boolean = true
) {
  def render = views.html.libratochart.render(this)
}

trait LibratoChartRequestFactoryModule {
  def libratoChartRequest: LibratoChartRequestFactory
  trait LibratoChartRequestFactory {
    def create(id:         Option[Int] = None,
               name:       Option[String] = None,
               duration:   Option[Long] = None,
               uploadToS3: Boolean = true
               ): Option[LibratoChartRequest]
  }
}

trait DefaultLibratoChartRequestFactoryModule extends LibratoChartRequestFactoryModule {
  this: ConfigModule =>

  def libratoChartRequest = DefaultLibratoChartRequestFactory

  object DefaultLibratoChartRequestFactory extends LibratoChartRequestFactory {

    lazy val username = config.get("librato.user")
    lazy val password = config.get("librato.password")

    def create(id:         Option[Int] = None,
               name:       Option[String] = None,
               duration:   Option[Long] = None,
               uploadToS3: Boolean = true) = {
      def result[T](f: ⇒ Future[Option[T]]) = Await.result(f, 10.milliseconds)
      (
        for (i <- id; n <- result(LibratoData.nameFromId(i)))
        yield i -> n
      ).orElse (
        for (n <- name; i <- result(LibratoData.idFromName(n)))
        yield i -> n
      ) map {
        case (i, n) =>
          LibratoChartRequest(
            username, password, i, n, duration.getOrElse(30.minutes.toSeconds), uploadToS3
          )
      }
    }
  }
}

trait LibratoChartModule {
  def libratoChart: LibratoChart
  trait LibratoChart {
    def createChart(request: LibratoChartRequest): Future[Array[Byte]]
    def createChartInS3(request: LibratoChartRequest): Future[String]
  }
}

trait DefaultLibratoChartModule extends LibratoChartModule {
  this: ConfigModule =>
  val libratoChart = DefaultLibratoChart

  object DefaultLibratoChart extends LibratoChart {
    lazy val key    = config.get("aws.accessKeyId")
    lazy val secret = config.get("aws.secretKey")
    lazy val bucket = config.get("aws.bucket")
    lazy val s3     = S3(bucket)(AwsCredentials(key, secret))


    def createChart(request: LibratoChartRequest)     = _createChart(request)
    def createChartInS3(request: LibratoChartRequest) = _createChartInS3(request)

    def _createChart     = writeChartHTML _ andThen generateImage
    def _createChartInS3 = _createChart andThen uploadToS3

    private def writeChartHTML(template: LibratoChartRequest) = {
      val tmpFile = File.createTempFile("chart", ".html")
      val writer = new FileWriter(tmpFile)
      writer.write(template.render.body)
      writer.close()
      tmpFile
    }

    private def generateImage(file: File): Future[Array[Byte]] = {
      ChartServer.chart(file).map(r => Base64.decode(r.body))
    }

    private def uploadToS3(dataFuture: Future[Array[Byte]]): Future[String] = {
      dataFuture.flatMap { data ⇒
        new String(data)
        val objName = UUID.randomUUID().toString + "librato-chart.png"
        s3.add(BucketFile(objName, "image/png", data, Some(PUBLIC_READ)))
          .map {
          case Left(err) =>
            "Unable to create image"
          case Right(res) =>
            "https://" + bucket + ".s3.amazonaws.com/" + objName.toString
        }
      }
    }
  }
}

object ChartServer {
  val chartServerHost = "localhost"
  val chartServerPort = "8080"
  val chartServerUrl  = s"http://${chartServerHost}:${chartServerPort}"

  implicit def string2bytearray(s: String) = s.getBytes("UTF-8")

  def chart(file: File) = {
    WS.url(chartServerUrl)
      .withHeaders(HttpHeaders.Names.CONTENT_TYPE -> "application/json")
      .post(Json.toJson(
        Map("input" → ("file://" + file.getAbsolutePath))
      ))
  }
}