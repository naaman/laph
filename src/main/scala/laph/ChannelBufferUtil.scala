package laph

import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.finagle.http.Response

object ChannelBufferUtil {
  def asByteArray(res: HttpResponse) = res.getContent.toByteBuffer.array()
  def asString(res: HttpResponse): String = Response(res).contentString
  def asJson[T: Manifest] = asString _ andThen JsonUtil.parse[T]
}
