package laph

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object JsonUtil {

  val mapper = new ObjectMapper()
                 .registerModule(DefaultScalaModule)
                 .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def parse[T: Manifest](s: String) =
    mapper.readValue(s, classManifest[T].erasure).asInstanceOf[T]

  def parse[T: Manifest](a: Array[Byte]) =
    mapper.readValue(a, classManifest[T].erasure).asInstanceOf[T]

  def generate = mapper.writeValueAsString _

}
