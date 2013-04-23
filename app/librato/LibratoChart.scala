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

case class LibratoChartRequest(
  username:   String,
  password:   String,
  id:         Int,
  name:       String,
  duration:   Long,
  uploadToS3: Boolean = true
)

case object LibratoChartRequest {
  val username = LibratoChart.cfg("LIBRATO_USER")
  val password = LibratoChart.cfg("LIBRATO_PASSWORD")
  def create(id:         Option[Int] = None,
             name:       Option[String] = None,
             duration:   Option[Long],
             uploadToS3: Boolean = true) = {
    def result[T](f: ⇒ Future[Option[T]]) = Await.result(f, 10.milliseconds)
    ( for (i <- id; n <- result(LibratoData.nameFromId(i)))
      yield i -> n
    ).orElse (
      for (n <- name; i <- result(LibratoData.idFromName(n)))
      yield i -> n
    ) map {
      case (i, n) ⇒
        LibratoChartRequest(
          username, password, i, n, duration.getOrElse(30.minutes.toSeconds), uploadToS3)
    }
  }

  def render(cr: LibratoChartRequest) = views.html.libratochart.render(cr)
}

object LibratoChart {

  def cfg(key: String) = {
    sys.env.get(key).getOrElse(sys.error("%s not configured".format(key)))
  }

  val key    = cfg("S3_KEY")
  val secret = cfg("S3_SECRET")
  val bucket = cfg("S3_BUCKET")
  val s3     = S3(bucket)(AwsCredentials(key, secret))

  def createChart = {
    writeChartHTML         _ andThen
    generateImage
  }

  def createChartInS3 = createChart andThen uploadToS3

  def writeChartHTML(template: LibratoChartRequest) = {
    val tmpFile = File.createTempFile("chart", ".html")
    val writer = new FileWriter(tmpFile)
    writer.write(LibratoChartRequest.render(template).body)
    writer.close()
    tmpFile
  }

  def generateImage(file: File): Future[Array[Byte]] = {
    ChartServer.chart(file)
      .map(r => Base64.decode(r.body))
  }

  def uploadToS3(dataFuture: Future[Array[Byte]]): Future[String] =
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