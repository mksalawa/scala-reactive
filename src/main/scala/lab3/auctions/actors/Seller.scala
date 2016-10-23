package lab3.auctions.actors

import akka.actor.{Actor, ActorPath, ActorRef, Props}
import akka.event.LoggingReceive
import lab3.auctions.actors.Auction.Win

import scala.collection.mutable
import scala.concurrent.duration._


object Seller {

  case class CreateAuctions(auctions: List[String]) {
    require(auctions.nonEmpty)
  }

  def props(searchPath: ActorPath, bidTime: FiniteDuration, deleteTime: FiniteDuration): Props =
    Props(new Seller(searchPath, bidTime, deleteTime))
}

class Seller(searchPath: ActorPath, bidTime: FiniteDuration, deleteTime: FiniteDuration) extends Actor {

  import lab3.auctions.actors.Seller._

  var auctions = new mutable.HashMap[String, ActorRef]()

  def receive = LoggingReceive {
    case CreateAuctions(titles) =>
      titles.zipWithIndex.foreach { case (t, i) =>
        val auction = context.actorOf(Auction.props(t, bidTime, deleteTime), "auction_" + t.replace(" ", "_"))
        auctions += t -> auction
        context.actorSelection(searchPath) ! AuctionSearch.Register(auction, t)
        Thread sleep 100
      }
    case AuctionData(title, currBid, currBuyer) =>
      println(s"Auction: $title sold to $currBuyer for $currBid")
      currBuyer ! Win(sender)
      auctions -= title
  }
}