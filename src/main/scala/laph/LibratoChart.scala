package laph

import java.io.{FileWriter, File}
import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{RequestBuilder, Http}
import com.twitter.util.{Base64StringEncoder ⇒ Base64, Future, Duration}
import laph.Templates.LibratoChartView
import laph.ChannelBufferUtil._
import com.twitter.conversions.time._
import org.jboss.netty.buffer.ChannelBuffers
import laph.S3.{S3Secret, S3Key}
import java.util.UUID

case class LibratoChartRequest(
  username:   String,
  password:   String,
  id:         Long,
  name:       Option[String] = None,
  uploadToS3: Boolean = true
) {
  def toView = LibratoChartView(username, password, id, name.getOrElse(""))
}

object LibratoChart {

  val key = S3Key(sys.env.get("S3_KEY").getOrElse(sys.error("S3_KEY not configured.")))
  val secret = S3Secret(sys.env.get("S3_SECRET").getOrElse(sys.error("S3_SECRET not configured.")))
  val bucket = sys.env.get("S3_BUCKET").getOrElse(sys.error("S3_BUCKET not configured."))
  val s3 = S3.client(key, secret)

  def createChart = {
    chartInfo              _ andThen
    createLibratoChartView _ andThen
    writeChartHTML         _ andThen
    generateImage
  }

  def createChartInS3 = uploadToS3 _ compose createChart

  def chartInfo(chartRequest: LibratoChartRequest) =
    Librato(chartRequest.username, chartRequest.password)
      .instrument(chartRequest.id)
      .map(inst ⇒ chartRequest.copy(name = Some(inst.name)))

  def createLibratoChartView(chartRequest: Future[LibratoChartRequest]) =
    chartRequest.map(_.toView)

  def writeChartHTML(libratoView: Future[LibratoChartView]) =
    libratoView.map { template ⇒
      val tmpFile = File.createTempFile("chart", ".html")
      val writer = new FileWriter(tmpFile)
      writer.write(template.render)
      writer.close()
      tmpFile
    }

  def generateImage(fileFuture: Future[File]): Future[Array[Byte]] =
    fileFuture.flatMap { file ⇒
      ChartServer.chart(file).map(asString _ andThen Base64.decode)
    }

  def uploadToS3(dataFuture: Future[Array[Byte]]): Future[String] =
    dataFuture.flatMap { data ⇒
      val objName = UUID.randomUUID().toString + "librato-chart.png"
      s3(Put(
        bucket,
        objName.toString,
        ChannelBuffers.copiedBuffer(data),
        "x-amz-acl" → "public-read",
        "Content-Type" → "image/png"
      )).map(_ ⇒ "https://" + bucket + ".s3.amazonaws.com/" + objName.toString)
    }
}

object ChartServer {
  val chartServerHost = "localhost"
  val chartServerHostWithPort = chartServerHost + ":8080"
  val tcpConnectTimeout: Duration = 60.seconds
  val hostConnectionLimit: Int = 50

  val client: Service[HttpRequest, HttpResponse] = ClientBuilder()
    .codec(Http())
    .hosts(chartServerHostWithPort)
    .tcpConnectTimeout(tcpConnectTimeout)
    .hostConnectionLimit(hostConnectionLimit)
    .logger(java.util.logging.Logger.getLogger("http"))
    .build()

  implicit def string2bytearray(s: String) = s.getBytes("UTF-8")

  def chart(file: File) = {
    client(
      RequestBuilder().url("http://localhost:8080")
        .setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json")
        .buildPost(ChannelBuffers.copiedBuffer(JsonUtil.generate(
          Map("input" → ("file://" + file.getAbsolutePath))
        )))
    )
  }
}