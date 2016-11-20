package lab5.auctions.actors

import java.util.concurrent.TimeoutException

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import lab5.auctions.actors.NotifierRequest.SendNotification

import scala.concurrent.Await
import scala.concurrent.duration._


object Notifier {
  case class Notify(auctionData: AuctionData)

  def props(publisherPath: String): Props = Props(new Notifier(publisherPath))
}

class Notifier(publisherPath: String) extends Actor {

  import Notifier._

  override val supervisorStrategy = OneForOneStrategy() {
    case NotifierRequest.NotificationFailed(auctionData) =>
      System.err.println("NOTIFICATION FAILED, restarting...")
      Restart
    case e: Exception =>
      System.err.println("EXCEPTION, stopping...")
      Stop
  }

  def receive = LoggingReceive {
    case Notify(auctionData) =>
      val request = context.actorOf(NotifierRequest.props(publisherPath))
      request ! SendNotification(auctionData)
  }
}

object NotifierRequest {
  case class NotificationFailed(auctionData: AuctionData) extends RuntimeException(auctionData.title)
  case class SendNotification(auctionData: AuctionData)


  def props(publisherPath: String): Props = Props(new NotifierRequest(publisherPath))
}

class NotifierRequest(publisherPath: String) extends Actor {
  import NotifierRequest._

  implicit val timeout = Timeout(10 seconds)

  def receive = LoggingReceive {
    case SendNotification(auctionData) =>
      try {
        val future = context.actorSelection(publisherPath) ? auctionData
        val result = Await.result(future, timeout.duration)
      } catch {
        case te: TimeoutException =>
          throw NotificationFailed(auctionData)
      }
  }
}
