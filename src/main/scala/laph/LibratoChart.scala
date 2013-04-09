package laph

import java.io.{FileWriter, File}
import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.http.{RequestBuilder, Http}
import com.twitter.util.{Base64StringEncoder => Base64, Future, Duration}
import laph.Templates.LibratoChartView
import laph.ChannelBufferUtil._
import com.twitter.conversions.time._
import org.jboss.netty.buffer.ChannelBuffers

case class LibratoChartRequest(
  username: String,
  password: String,
  id: Long,
  name: Option[String] = None
) {
  def toView = LibratoChartView(username, password, id, name.getOrElse(""))
}

object LibratoChart {

  def createChart = {
    chartInfo              _ andThen
    createLibratoChartView _ andThen
    writeChartHTML         _ andThen
    generateImage
  }

  def chartInfo(chartRequest: LibratoChartRequest) =
    Librato(chartRequest.username, chartRequest.password)
      .instrument(chartRequest.id)
      .map(inst => chartRequest.copy(name = Some(inst.name)))

  def createLibratoChartView(chartRequest: Future[LibratoChartRequest]) =
    chartRequest.map(_.toView)

  def writeChartHTML(libratoView: Future[LibratoChartView]) =
    libratoView.map { template =>
      val tmpFile = File.createTempFile("chart", ".html")
      val writer = new FileWriter(tmpFile)
      writer.write(template.render)
      writer.close()
      tmpFile
    }

  def generateImage(fileFuture: Future[File]): Future[Array[Byte]] =
    fileFuture.flatMap { file =>
      ChartServer.chart(file).map(asString _ andThen Base64.decode)
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