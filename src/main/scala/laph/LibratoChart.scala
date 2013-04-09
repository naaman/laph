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

object LibratoChart {

  def createChart = writeChartHTML _ andThen generateImage

  def writeChartHTML(libratoView: LibratoChartView) = {
    val tmpFile = File.createTempFile("chart", ".html")
    val writer = new FileWriter(tmpFile)
    writer.write(libratoView.render)
    writer.close()
    tmpFile
  }

  def generateImage(file: File): Future[Array[Byte]] = {
    ChartServer.chart(file).map(asString _ andThen Base64.decode)
  }

}

object ChartServer {
  val chartServerHost = ""
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
    val payload: String = JsonUtil.generate(Map("input" -> ("file://" + file.getAbsolutePath)))
    client(
      RequestBuilder().url("http://localhost:8080")
        .setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json")
        .buildPost(ChannelBuffers.copiedBuffer(payload))
    )
  }
}