package lab4.efficiency

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.persistence.PersistentActor

import scala.concurrent.Await
import scala.concurrent.duration._


// states
sealed trait MoodState
case object Happy extends MoodState
case object Sad extends MoodState

case class MoodChangeEvent(state: MoodState)


class PersistentToggle extends PersistentActor {

  override def persistenceId = "persistent-toggle-id-1"

  def updateState(event: MoodChangeEvent): Unit =
    context.become(
      event.state match {
        case Happy => happy
        case Sad => sad
      })

  def happy: Receive = LoggingReceive {
    case "How are you?" =>
      persist(MoodChangeEvent(Sad)) {
        event =>
          updateState(event)
          sender ! "happy"
      }
    case "Done" =>
      sender ! "Done"
  }

  def sad: Receive = LoggingReceive {
    case "How are you?" =>
      persist(MoodChangeEvent(Happy)) {
        event =>
          updateState(event)
          sender ! "sad"
      }

    case "Done" =>
      sender ! "Done"
  }
  def receiveCommand = happy

  val receiveRecover: Receive = {
    case evt: MoodChangeEvent => updateState(evt)
  }
}


class EfficiencyMain extends Actor {

  val toggle = context.actorOf(Props[PersistentToggle], "toggle")

  var start = System.currentTimeMillis()

  (1 to 20).foreach(rep => {
    (1 to 10000).foreach(i => {
      toggle ! "How are you?"
    })
    toggle ! "Done"
  })

  def receive = LoggingReceive {
    case "Done" =>
      val time: Long = System.currentTimeMillis() - start
      println(time)
      start = System.currentTimeMillis()

    case "Finished" =>
      context.system.terminate()
    case msg: String =>
//      println(s" received: $msg")
  }
}


object EfficiencyApp extends App {
  val system = ActorSystem("AuctionSystem")
  val mainActor = system.actorOf(Props[EfficiencyMain], "mainActor")

  Await.result(system.whenTerminated, Duration.Inf)
}
