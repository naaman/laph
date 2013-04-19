package librato

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{TimeUnit, ConcurrentHashMap}
import akka.util.Timeout

object LibratoData {
  val system = ActorSystem("librato-data")

  val dataActorRef = system.actorOf(Props[LibratoDataActor], name = "librato-data-actor")

  def poll(account: LibratoAccount) = {
    system.scheduler.schedule(0 seconds, 10 minutes, dataActorRef, account)
  }

  implicit val timeout = Timeout(10, TimeUnit.MILLISECONDS)
  def idFromName(name: String) = ask(dataActorRef, FindDataFromName(name)).mapTo[Option[Int]]
  def nameFromId(id: Int)      = ask(dataActorRef, FindDataFromId(id)).mapTo[Option[String]]
}

class LibratoDataActor extends Actor {
  val data = new ConcurrentHashMap[Int, String]
  private def findData(ƒ: ((Int, String)) => Boolean) = {
    import scala.collection.JavaConversions._
    data.find(ƒ(_))
  }

  def receive = {
    case LibratoAccount(u, p) ⇒
      Librato(u, p).allInstruments.map { instruments ⇒
        instruments.foreach(inst ⇒ data.put(inst.id, inst.name))
      }
    case FindDataFromId(id) ⇒
      sender ! findData(_._1.equals(id)).map(_._2)
    case FindDataFromName(name) ⇒
      sender ! findData(_._2.equals(name)).map(_._1)
  }
}

case class LibratoAccount(username: String, password: String)
case class FindDataFromId(id: Int)
case class FindDataFromName(name: String)