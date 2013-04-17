package laph

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.duration._
import java.util.concurrent.{TimeUnit, ConcurrentHashMap}
import akka.util.Timeout

object LibratoData {
  val system = ActorSystem("librato-data")

  val dataActorRef = system.actorOf(Props[LibratoDataActor], name = "librato-data-actor")

  def poll(account: LibratoAccount) = {
    system.scheduler.schedule(0 seconds, 10 minutes, dataActorRef, account)
  }

  implicit val timeout = Timeout(10, TimeUnit.MILLISECONDS)
  def idFromName(name: String) = ask(dataActorRef, FindDataFromName(name)).mapTo[Option[Long]]
  def nameFromId(id: Long)     = ask(dataActorRef, FindDataFromId(id)).mapTo[Option[String]]
}

class LibratoDataActor extends Actor {
  val data = new ConcurrentHashMap[Long, String]
  private def findData(ƒ: ((Long, String)) => Boolean) = {
    import scala.collection.JavaConversions._
    data.find(ƒ(_))
  }

  protected def receive = {
    case LibratoAccount(u, p) ⇒
      Librato(u, p).allInstruments.map { instruments ⇒
        instruments.foreach(inst ⇒ data.put(inst.id, inst.name))
      }.map { _ ⇒
        println("Librato Data" + data)
      }
    case FindDataFromId(id) ⇒
      sender ! findData(_._1.equals(id)).map(_._2)
    case FindDataFromName(name) ⇒
      sender ! findData(_._2.equals(name)).map(_._1)
  }
}

case class LibratoAccount(username: String, password: String)
case class FindDataFromId(id: Long)
case class FindDataFromName(name: String)