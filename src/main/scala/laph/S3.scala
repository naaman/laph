package laph

import com.twitter.finagle.http.Http
import com.twitter.finagle.http.netty.{HttpResponseProxy, HttpRequestProxy}
import org.jboss.netty.channel._
import org.joda.time.format.DateTimeFormat
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.jboss.netty.handler.codec.http.HttpVersion._
import com.twitter.finagle._
import builder.ClientBuilder
import org.jboss.netty.handler.codec.http.HttpMethod._
import org.jboss.netty.handler.codec.http._
import org.joda.time.{DateTime, DateTimeZone}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.logging.Logger
import org.jboss.netty.util.CharsetUtil._
import collection.mutable.HashMap
import annotation.implicitNotFound
import com.twitter.util.StorageUnit
import com.twitter.conversions.storage._
import com.twitter.conversions.time._

object S3 {
  type S3Client = Service[S3Request, HttpResponse]

  @implicitNotFound(msg = "cannot find implicit S3Key in scope")
  case class S3Key(key: String)

  @implicitNotFound(msg = "cannot find implicit S3Secret in scope")
  case class S3Secret(secret: String)


  def get(key: String, secret: String) = new S3(key, secret)

  def client(key: S3Key, secret: S3Secret, name: String = "S3Client", maxReq: StorageUnit = 100.megabytes, maxRes: StorageUnit = 100.megabytes): S3Client = {
    ClientBuilder().codec(S3(key.key, secret.secret, Http(_maxRequestSize = maxReq, _maxResponseSize = maxRes)))
      .sendBufferSize(262144)
      .recvBufferSize(262144)
      .hosts("s3.amazonaws.com:80")
      .timeout(30.seconds)
      .tcpConnectTimeout(2.seconds)
      .hostConnectionLimit(Integer.MAX_VALUE)
      .logger(java.util.logging.Logger.getLogger("http"))
      .name(name)
      .build()
  }
}

case class S3(private val key: String, private val secret: String, httpFactory: CodecFactory[HttpRequest, HttpResponse] = Http.get()) extends CodecFactory[S3Request, HttpResponse] {

  def client = Function.const {
    new Codec[S3Request, HttpResponse] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline = {
          val pipeline = httpFactory.client(null).pipelineFactory.getPipeline
          pipeline.addLast("requestEncoder", new RequestEncoder(key, secret))
          pipeline
        }
      }
    }
  }

  def server = throw new UnsupportedOperationException("This is a client side only codec factory")
}

class RequestEncoder(key: String, secret: String) extends SimpleChannelDownstreamHandler {

  val log = Logger.get(classOf[RequestEncoder])

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case bucketRequest: BucketRequest =>
        prepare(bucketRequest)
        ctx.sendDownstream(e)
      case serviceRequest: ServiceRequest =>
        prepare(serviceRequest)
        ctx.sendDownstream(e)
      case unknown =>
        ctx.sendDownstream(e)
    }
  }

  def prepare(req: BucketRequest) {
    req.setHeaders(HOST -> bucketHost(req.bucket), DATE -> amzDate)
    req.setHeaders(AUTHORIZATION -> authorization(key, secret, req))
    //Add query params after signing
    if (req.queries.size > 0)
      req.setUri(req.getUri + "?" + req.queries.map(qp => (qp._1 + "=" + qp._2)).reduceLeft(_ + "&" + _))
  }

  def prepare(req: ServiceRequest) {
    req.setHeaders(HOST -> "s3.amazonaws.com", DATE -> amzDate)
    req.setHeaders(AUTHORIZATION -> authorization(key, secret, req))
  }

  /*DateTime format required by AWS*/
  lazy val amzFormat = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z").withLocale(java.util.Locale.US).withZone(DateTimeZone.UTC)

  /*headers used by this app that need to be used in signing*/
  //TODO make this extensible
  val SOURCE_ETAG = "x-amz-meta-source-etag"
  val SOURCE_MOD = "x-amz-meta-source-mod"
  val COPY_SOURCE = "x-amz-copy-source"
  val ACL = "x-amz-acl"
  val STORAGE_CLASS = "x-amz-storage-class"
  val VERSION = "x-amz-version-id"
  //headers need to be in alphabetical order in this list
  val AMZN_HEADERS = List(ACL, COPY_SOURCE, SOURCE_ETAG, SOURCE_MOD, STORAGE_CLASS, VERSION)

  val RRS = "REDUCED_REDUNDANCY"
  val ALGORITHM = "HmacSHA1"


  def amzDate: String = amzFormat.print(DateTime.now())


  /*request signing for amazon*/
  /*Create the Authorization payload and sign it with the AWS secret*/
  def sign(secret: String, request: BucketRequest): String = {
    val data = List(
      request.getMethod().getName,
      request.header(CONTENT_MD5).getOrElse(""),
      request.header(CONTENT_TYPE).getOrElse(""),
      request.getHeader(DATE)
    ).foldLeft("")(_ + _ + "\n") + normalizeAmzHeaders(request) + "/" + request.bucket + request.getUri
    log.debug("String to sign")
    log.debug(data)
    calculateHMAC(secret, data)
  }


  def sign(secret: String, request: ServiceRequest): String = {
    val data = List(
      request.getMethod().getName,
      request.header(CONTENT_MD5).getOrElse(""),
      request.header(CONTENT_TYPE).getOrElse(""),
      request.getHeader(DATE)
    ).foldLeft("")(_ + _ + "\n") + normalizeAmzHeaders(request) + request.getUri
    log.debug("String to sign")
    log.debug(data)
    calculateHMAC(secret, data)
  }


  def normalizeAmzHeaders(request: S3Request): String = {
    AMZN_HEADERS.foldLeft("") {
      (str, h) => {
        request.header(h).flatMap(v => Some(str + h + ":" + v + "\n")).getOrElse(str)
      }
    }
  }

  def bucketHost(bucket: String) = bucket + ".s3.amazonaws.com"

  def authorization(s3key: String, s3Secret: String, req: BucketRequest): String = {
    "AWS " + s3key + ":" + sign(s3Secret, req)
  }

  def authorization(s3key: String, s3Secret: String, req: ServiceRequest): String = {
    "AWS " + s3key + ":" + sign(s3Secret, req)
  }


  private def calculateHMAC(key: String, data: String): String = {
    val signingKey = new SecretKeySpec(key.getBytes(UTF_8), ALGORITHM)
    val mac = Mac.getInstance(ALGORITHM)
    mac.init(signingKey)
    val rawHmac = mac.doFinal(data.getBytes)
    new sun.misc.BASE64Encoder().encode(rawHmac)
  }

}

case class ServiceRequest(url: String) extends S3Request {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, GET, url);
}

trait S3Request extends HttpRequestProxy {

  def setHeaders(headers: (String, String)*) = {
    headers.foreach(h => httpRequest.setHeader(h._1, h._2))
    this
  }

  def header(name: String): Option[String] = {
    Option(httpRequest.getHeader(name))
  }

}

trait BucketRequest extends S3Request {

  def bucket: String

  def sign: Boolean = true

  val queries = new HashMap[String, String]

  def normalizeKey(key: String) = {
    if (key.startsWith("/")) key
    else "/" + key
  }

  def query(q: (String, String)*) = {
    q.foreach(kv => queries += kv._1 -> kv._2)
    this
  }
}

trait ObjectRequest extends BucketRequest {
  def key: String
}

case class Put(bucket: String, key: String, content: ChannelBuffer, headers: (String, String)*) extends ObjectRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, PUT, normalizeKey(key));
  setContent(content)
  setHeader(CONTENT_LENGTH, content.readableBytes().toString)
  headers.foreach(h => setHeader(h._1, h._2))
}

case class Get(bucket: String, key: String, override val sign: Boolean = true) extends ObjectRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, GET, normalizeKey(key));
}

case class Head(bucket: String, key: String, override val sign: Boolean = true) extends ObjectRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, HEAD, normalizeKey(key));
}

case class CreateBucket(bucket: String) extends BucketRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, PUT, "/");
  setHeader(CONTENT_LENGTH, "0")
}

case class DeleteBucket(bucket: String) extends BucketRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, DELETE, "/");
}

case class Delete(bucket: String, key: String) extends ObjectRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, DELETE, normalizeKey(key));
  setHeader(CONTENT_LENGTH, "0")
}

case class ListBucket(bucket: String, marker: Option[Marker] = None, prefix: Option[Prefix] = None) extends BucketRequest {
  override val httpRequest: HttpRequest = new DefaultHttpRequest(HTTP_1_1, GET, "/");
  marker.foreach(m => query("marker" -> m.marker))
  prefix.foreach(p => query("prefix" -> p.prefix))
}

case class Marker(marker: String)

case class Prefix(prefix: String) {
  def path(local: String): String = {
    if (local.startsWith("/")) prefix + local
    else prefix + "/" + local
  }
}

case class ObjectInfo(key: String, lastModified: String, etag: String, size: String, storageClass: String, ownerId: String, ownerDisplayName: String)

class S3Response(resp: HttpResponse) extends HttpResponseProxy {
  def httpResponse = resp
}