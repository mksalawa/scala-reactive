package lab3.auctions.actors

import akka.actor.{Actor, ActorPath, ActorRef}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random


object Buyer {
  case class Buy(shoppingList: List[String])
}

class Buyer(searchPath: ActorPath) extends Actor {
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
      val bid = new Random().nextInt(20) + 1
      auction ! Bid(bid, self)
      activeAuctions += auction -> bid
    case Auction.BidSuccess(auction) =>
      println(s"Bid success: $self -> $sender")
    case Auction.BidFailed(auction) =>
      println(s"Bid failed: $self -> $sender")
      val newBid = new Random().nextInt(20) + 1
      auction ! Bid(activeAuctions(auction) + newBid, self)
      activeAuctions(auction) = newBid
    case Auction.Win(auction) =>
      println(s"Won auction: $self -> $auction")
      activeAuctions -= auction
  }
}
