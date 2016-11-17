package lab4.auctions.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.forkjoin.ThreadLocalRandom


object Buyer {
  case class Buy(shoppingList: List[String])

  def props(searchPath: String, maxBid: BigInt): Props = Props(new Buyer(searchPath, maxBid))
}

class Buyer(searchPath: String, maxBid: BigInt) extends Actor {
  import Buyer._

  var shopping = new ArrayBuffer[String]()
  val activeAuctions = new mutable.HashMap[String, BigInt]()

  def getPath(ref: ActorRef): String = ref.path.toStringWithoutAddress

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
      val auctionPath = results.head
      val bid = ThreadLocalRandom.current().nextInt(maxBid.intValue() - 1) + 1
      context.actorSelection(auctionPath) ! Auction.Bid(bid, self)
      activeAuctions += auctionPath -> bid
      Thread sleep 500
    case Auction.BidSuccess(auction) =>
      println(s"BID SUCCESS | ${self.path.name} | ${auction.path.name} (bid: ${activeAuctions(getPath(auction))})")
    case Auction.BidFailed(auction) =>
      println(s"BID FAILED | ${self.path.name} | ${auction.path.name} (bid: ${getBid(getPath(auction))})")
      val newBid = getNewBid(getPath(auction))
      if (newBid <= maxBid) {
        println(s"Bidding | ${self.path.name} | ${auction.path.name} (new bid: $newBid)")
        auction ! Auction.Bid(newBid, self)
        activeAuctions(getPath(auction)) = newBid
      } else {
        println(s"Resigning from bidding | ${self.path.name} | ${auction.path.name} ($newBid exceeds max bid $maxBid)")
        activeAuctions -= getPath(auction)
      }
      Thread sleep 500
    case Auction.Win(auction) =>
      println(s"WINNER | ${self.path.name} | ${auction.path.name}")
      activeAuctions -= getPath(auction)
    case Auction.BidRaised(auction) =>
      println(s"BID RAISE | ${self.path.name} | ${auction.path.name}")
      val newBid = getNewBid(getPath(auction))
      if (newBid <= maxBid) {
        println(s"Bidding | ${self.path.name} | ${auction.path.name} (new bid: $newBid)")
        auction ! Auction.Bid(newBid, self)
        activeAuctions(getPath(auction)) = newBid
      } else {
        println(s"Resigning from bidding | ${self.path.name} | ${auction.path.name} ($newBid exceeds max bid $maxBid)")
        activeAuctions -= getPath(auction)
      }
      Thread sleep 500
  }

  def getNewBid(auctionPath: String): BigInt = {
    var newBid: BigInt = 0
    if (activeAuctions.contains(auctionPath)) {
      newBid = activeAuctions(auctionPath) + 2
    } else {
      newBid = BigInt.apply(ThreadLocalRandom.current().nextInt(maxBid.intValue() - 1) + 1)
    }
    newBid
  }

  def getBid(auctionPath: String): BigInt = {
    if (activeAuctions.contains(auctionPath)) {
      return activeAuctions(auctionPath)
    }
    -1
  }
}
