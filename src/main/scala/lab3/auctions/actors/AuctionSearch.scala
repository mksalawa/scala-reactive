package lab3.auctions.actors

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive

import scala.collection.mutable


object AuctionSearch {
  case class Register(auction: ActorRef, title: String)
  case class Search(query: String)
  case class SearchResult(query: String, result: List[ActorRef])
}

class AuctionSearch() extends Actor {
  import lab3.auctions.actors.AuctionSearch._

  var auctions = new mutable.HashMap[String, ActorRef]()

  def receive = LoggingReceive {
    case Register(auction, title) =>
      auctions += (title -> auction)
    case Search(query) =>
      sender ! SearchResult(query, auctions.filterKeys(key => key.toLowerCase.contains(query.toLowerCase)).values.toList)
  }
}