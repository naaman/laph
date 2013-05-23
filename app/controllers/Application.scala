package controllers

import util.{ConfigModule, PlayConfigModule}
import play.api.mvc._
import play.api.libs.json._
import librato._
import scala.concurrent.ExecutionContext.Implicits.global

trait LibratoChartController {
  this:  Controller
    with ConfigModule
    with LibratoChartRequestFactoryModule
    with LibratoChartModule =>

  implicit val chartReads = Json.reads[ChartJsonRequest]

  def createChartInS3 = Action(parse.json) { request ⇒
    request.body.validate[ChartJsonRequest].map { cjr =>
      (for {
        chartReq ← libratoChartRequest.create(cjr.chartId.map(_.toInt), cjr.chartName, cjr.duration)
      } yield {
        Async {
          libratoChart.createChartInS3(chartReq).map(s => Ok(Json.toJson(s)))
        }
      }).getOrElse(NotFound)
    }.recoverTotal(e => BadRequest(JsError.toFlatJson(e)))
  }
}

object LibratoChartController
  extends Controller
     with LibratoChartController
     with DefaultLibratoChartRequestFactoryModule
     with DefaultLibratoChartModule
     with PlayConfigModule

case class ChartCreateRequest(chartId: Int)
case class ChartJsonRequest(chartId: Option[Long], chartName: Option[String], duration: Option[Long])