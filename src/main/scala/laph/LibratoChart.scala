package laph

import java.io.{FileWriter, File}
import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{RequestBuilder, Http}
import com.twitter.util.{Base64StringEncoder ⇒ Base64, Future ⇒ TwFuture, Duration}
import akka.dispatch.{Future ⇒ AkkaFuture}
import laph.Templates.LibratoChartView
import laph.ChannelBufferUtil._
import com.twitter.conversions.time._
import org.jboss.netty.buffer.ChannelBuffers
import laph.S3.{S3Secret, S3Key}
import java.util.UUID
import com.twitter.util

case class LibratoChartRequest(
  username:   String,
  password:   String,
  id:         Option[Long] = None,
  name:       Option[String] = None,
  uploadToS3: Boolean = true
) {
  def toView = LibratoChartView(
    username,
    password,
    id.getOrElse(-1),
    name.getOrElse("")
  )
}

object LibratoChart {

  private def cfg(key: String) = {
    sys.env.get(key).getOrElse(sys.error("%s not configured".format(key)))
  }

  val key    = S3Key(cfg("S3_KEY"))
  val secret = S3Secret(cfg("S3_SECRET"))
  val bucket = cfg("S3_BUCKET")
  val s3     = S3.client(key, secret)

  def createChart = {
    chartInfo              _ andThen
    createLibratoChartView _ andThen
    writeChartHTML         _ andThen
    generateImage
  }

  def createChartInS3 = createChart andThen uploadToS3

  def chartInfo(chartRequest: LibratoChartRequest): TwFuture[LibratoChartRequest] = {
    val chartId   = chartRequest.id
    val chartName = chartRequest.name
    if (chartId.isDefined && chartName.isDefined)
      TwFuture.value(chartRequest)
    else if (chartId.isDefined)
      LibratoData.nameFromId(chartId.get).map(s ⇒ chartRequest.copy(name = s))
    else if (chartName.isDefined)
      LibratoData.idFromName(chartName.get).map(i ⇒ chartRequest.copy(id = i))
    else
      sys.error("ChartID or Chart Name must be supplied.")
  }

  def createLibratoChartView(chartRequest: TwFuture[LibratoChartRequest]) =
    chartRequest.map(_.toView)

  def writeChartHTML(libratoView: TwFuture[LibratoChartView]) =
    libratoView.map { template ⇒
      val tmpFile = File.createTempFile("chart", ".html")
      val writer = new FileWriter(tmpFile)
      writer.write(template.render)
      writer.close()
      tmpFile
    }

  implicit def akkaFuture2finagle[A](future: AkkaFuture[A]): TwFuture[A] = {
    val p = new util.Promise[A]
    future.onComplete {
      case Right(a) ⇒ p.setValue(a)
      case Left(a)  ⇒ p.setException(a)
    }
    p
  }

  def generateImage(fileFuture: TwFuture[File]): TwFuture[Array[Byte]] =
    fileFuture.flatMap { file ⇒
      ChartServer.chart(file)
        .map(asString _ andThen Base64.decode)
    }

  def uploadToS3(dataFuture: TwFuture[Array[Byte]]): TwFuture[String] =
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