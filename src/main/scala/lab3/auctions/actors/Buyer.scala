package lab3.auctions.actors

import akka.actor.{Actor, ActorPath, ActorRef, Props}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random


object Buyer {
  case class Buy(shoppingList: List[String])

  def props(searchPath: ActorPath, maxBid: BigInt): Props = Props(new Buyer(searchPath, maxBid))
}

class Buyer(searchPath: ActorPath, maxBid: BigInt) extends Actor {
  import lab3.auctions.actors.Buyer._

  var shopping = new ArrayBuffer[String]()
  val activeAuctions = new mutable.HashMap[ActorRef, BigInt]()

  def receive = LoggingReceive {
    case Buy(shoppingList) =>
      shopping ++ shoppingList
      shoppingList.foreach(item => context.actorSelection(searchPath) ! AuctionSearch.Search(item))
    case AuctionSearch.SearchResult(query, results) if results.isEmpty =>
      println(s"No auctions found for: $query")
      shopping -= query
    case AuctionSearch.SearchResult(query, results) =>
      shopping -= query
      // we take first result in the basic solution
      val auction = results.head
      val bid = new Random().nextInt(maxBid.intValue() - 1) + 1
      auction ! Auction.Bid(bid, self)
      activeAuctions += auction -> bid
    case Auction.BidSuccess(auction) =>
      println(s"Bid success: ${self.path.name} -> ${auction.path.name} (bid: ${activeAuctions(auction)})")
    case Auction.BidFailed(auction) =>
      println(s"Bid failed: ${self.path.name} -> ${auction.path.name} (bid: ${activeAuctions(auction)})")
      val newBid = activeAuctions(auction) + 1
      if (newBid <= maxBid) {
        auction ! Auction.Bid(newBid, self)
        activeAuctions(auction) = newBid
      } else {
        println(s"Resigning from bidding for: ${self.path.name} -> ${auction.path.name} ($newBid exceeds max bid $maxBid)")
        activeAuctions -= auction
      }
    case Auction.Win(auction) =>
      println(s"Won auction: ${self.path.name} -> ${auction.path.name} (bid: ${activeAuctions(auction)})")
      activeAuctions -= auction
    case Auction.BidRaised(auction) =>
      println(s"Bid raised for: ${self.path.name} -> ${auction.path.name}")
      val newBid = activeAuctions(auction) + 2
      if (newBid <= maxBid) {
        auction ! Auction.Bid(newBid, self)
        activeAuctions(auction) = newBid
      } else {
        println(s"Resigning from bidding for: ${self.path.name} -> ${auction.path.name} ($newBid exceeds max bid $maxBid)")
        activeAuctions -= auction
      }
  }
}
