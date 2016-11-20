package lab5.auctions.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive


object Notifier {
  case class Notify(auctionData: AuctionData)

  def props(publisherPath: String): Props = Props(new Notifier(publisherPath))
}

class Notifier(publisherPath: String) extends Actor {

  import Notifier._

  def receive = LoggingReceive {
    case Notify(auctionData) =>
      context.actorSelection(publisherPath) ! auctionData
  }
}
