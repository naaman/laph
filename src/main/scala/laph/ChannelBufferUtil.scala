package laph

import org.jboss.netty.handler.codec.http.HttpResponse

object ChannelBufferUtil {
  def asByteArray(res: HttpResponse) = res.getContent.toByteBuffer.array()
  def asString(res: HttpResponse): String = new String(asByteArray(res))
  def asJson[T: Manifest] = asByteArray _ andThen JsonUtil.parse[T]
}
