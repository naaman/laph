package controllers

import play.api.mvc._
import play.api.libs.json._
import librato._
import scala.concurrent.ExecutionContext.Implicits.global

object LibratoChartController extends Controller {

  implicit val chartReads = Json.reads[ChartJsonRequest]

  def createChartInS3 = Action(parse.json) { request ⇒
    request.body.validate[ChartJsonRequest].map { cjr =>
      (for {
        chartReq ← LibratoChartRequest.create(cjr.chartId.map(_.toInt), cjr.chartName, cjr.duration)
      } yield {
        Async {
          LibratoChart.createChartInS3(chartReq).map(s => Ok(Json.toJson(s)))
        }
      }).getOrElse(NotFound)
    }.recoverTotal(e => BadRequest(JsError.toFlatJson(e)))
  }
}

case class ChartCreateRequest(chartId: Int)
case class ChartJsonRequest(chartId: Option[Long], chartName: Option[String], duration: Option[Long])