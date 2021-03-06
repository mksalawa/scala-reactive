package lab5.auctions.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import lab5.auctions.actors.Auction.Win

import scala.collection.mutable
import scala.concurrent.duration._


object Seller {
  case object SellerFinished
  case class CreateAuctions(auctions: List[String]) {
    require(auctions.nonEmpty)
  }

  def props(searchPath: String, notifierPath: String, bidTime: FiniteDuration, deleteTime: FiniteDuration): Props =
    Props(new Seller(searchPath, notifierPath, bidTime, deleteTime))
}

class Seller(searchPath: String, notifierPath: String, bidTime: FiniteDuration, deleteTime: FiniteDuration) extends Actor {

  import Seller._

  var auctions = new mutable.HashSet[String]()

  def receive = LoggingReceive {
    case CreateAuctions(titles) =>
      titles.zipWithIndex.foreach { case (t, i) =>
        val name: String = "auction_" + t.replace(" ", "_")
        val auction = context.actorOf(Auction.props(name, t, bidTime, deleteTime, notifierPath), name)
        auctions += t
        context.actorSelection(searchPath) ! AuctionSearch.Register(auction, t)
      }
    case AuctionData(title, currBid, currBuyer, age) =>
      println(s"SOLD | $title | $currBuyer | $currBid")
      context.actorSelection(currBuyer) ! Win(sender)
      auctions -= title
      if (auctions.isEmpty) {
        context.parent ! SellerFinished
      }
    case Auction.AuctionExpired(AuctionData(t, currBid, currBuyer, timeout)) =>
      println(s"Expired | $t ")
      auctions -= t
      if (auctions.isEmpty) {
        context.parent ! SellerFinished
      }
  }
}