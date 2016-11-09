package lab4.auctions.actors

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
  import Buyer._

  var shopping = new ArrayBuffer[String]()
  val activeAuctions = new mutable.HashMap[ActorRef, BigInt]()

  def receive = LoggingReceive {
    case Buy(shoppingList) =>
      println(s"Received shopping list | ${self.path.name} | $shoppingList")
      shopping ++ shoppingList
      shoppingList.foreach(item => context.actorSelection(searchPath) ! AuctionSearch.Search(item))
    case AuctionSearch.SearchResult(query, results) if results.isEmpty =>
      println(s"No auctions found | ${self.path.name} | $query")
      shopping -= query
    case AuctionSearch.SearchResult(query, results) =>
      println(s"Auctions found | ${self.path.name} | $query, bidding...")
      shopping -= query
      // we take first result in the basic solution
      val auction = results.head
      val bid = new Random().nextInt(maxBid.intValue() - 1) + 1
      auction ! Auction.Bid(bid, self)
      activeAuctions += auction -> bid
      Thread sleep 100
    case Auction.BidSuccess(auction) =>
      println(s"BID SUCCESS | ${self.path.name} | ${auction.path.name} (bid: ${activeAuctions(auction)})")
    case Auction.BidFailed(auction) =>
      println(s"BID FAILED | ${self.path.name} | ${auction.path.name} (bid: ${activeAuctions(auction)})")
      val newBid = activeAuctions(auction) + 1
      if (newBid <= maxBid) {
        println(s"Bidding | ${self.path.name} | ${auction.path.name} (new bid: $newBid)")
        auction ! Auction.Bid(newBid, self)
        activeAuctions(auction) = newBid
      } else {
        println(s"Resigning from bidding | ${self.path.name} | ${auction.path.name} ($newBid exceeds max bid $maxBid)")
        activeAuctions -= auction
      }
      Thread sleep 100
    case Auction.Win(auction) =>
      println(s"WINNER | ${self.path.name} | ${auction.path.name} (bid: ${activeAuctions(auction)})")
      activeAuctions -= auction
    case Auction.BidRaised(auction) =>
      println(s"BID RAISE | ${self.path.name} | ${auction.path.name}")
      val newBid = activeAuctions(auction) + 2
      if (newBid <= maxBid) {
        println(s"Bidding | ${self.path.name} | ${auction.path.name} (new bid: $newBid)")
        auction ! Auction.Bid(newBid, self)
        activeAuctions(auction) = newBid
      } else {
        println(s"Resigning from bidding | ${self.path.name} | ${auction.path.name} ($newBid exceeds max bid $maxBid)")
        activeAuctions -= auction
      }
      Thread sleep 100
  }
}
