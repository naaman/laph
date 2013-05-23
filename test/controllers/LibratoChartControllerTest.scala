package controllers

import org.specs2.mutable._
import org.specs2.mock._
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc._
import librato._
import scala.concurrent.{Await, Future}
import play.api.libs.json.{JsNumber, JsValue, JsObject}
import util.MockConfigModule
import scala.concurrent.duration.Duration
import librato.LibratoChartRequest
import play.api.test.FakeHeaders
import scala.Some
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import librato.LibratoChartRequest
import play.api.test.FakeHeaders
import play.api.mvc.AsyncResult
import scala.Some
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.iteratee.{Iteratee, Enumeratee}
import scala.concurrent.ExecutionContext.Implicits.global

class LibratoChartControllerTest extends Specification with Mockito {

  val defaultConfig = Map(
    "librato.user"     -> "user@librato.com",
    "librato.password" -> "password",
    "aws.accessKeyId"  -> "ACCESS123",
    "aws.secretKey"    -> "SECRET123",
    "aws.bucket"       -> "bucket"
  )

  val defaultChartRequest = LibratoChartRequest("user@librato.com", "password", 1234, "chart.name", 1800)

  def mockLibratoChartController(appConfig: Option[Map[String, String]] = Some(defaultConfig),
                                 chartRequest: Option[LibratoChartRequest] = Some(defaultChartRequest)) = {
    new LibratoChartController
      with Controller
      with LibratoChartModule
      with LibratoChartRequestFactoryModule
      with MockConfigModule {
      def configMap: Map[String, String] = appConfig.getOrElse(Map.empty)

      override val libratoChartRequest = mock[LibratoChartRequestFactory].smart
      libratoChartRequest.create(any, any, any, any) returns chartRequest

      val chartUrl = "https://b.s3.amazonaws.com/uuid.png"
      override val libratoChart = mock[LibratoChart].smart
      libratoChart.createChartInS3(any) returns Future.successful(chartUrl)
    }
  }

  "POST /chart" should {
    "return 200 when a chartId is correct" in {
      val controller = mockLibratoChartController()
      val result = controller.createChartInS3()(
        FakeRequest(POST, "/chart")
          .withBody(JsObject(Seq("chartId" -> JsNumber(1234))))
      )
      contentAsString(result) must contain("https://b.s3.amazonaws.com/uuid.png")
    }
  }
}
