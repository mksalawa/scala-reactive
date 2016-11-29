package lab6.auctions.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import akka.routing._
import scala.concurrent.duration._


object MasterSearch {
  def props(routeesCount: Int): Props = Props(new MasterSearch(routeesCount))
}

class MasterSearch(routeesCount: Int) extends Actor {

  val routees = Vector.fill(routeesCount) {
    val r = context.actorOf(Props[AuctionSearch])
    context watch r
    ActorRefRoutee(r)
  }

  var registerRouter = {
    Router(BroadcastRoutingLogic(), routees)
  }

  var searchRouter = {
    Router(RoundRobinRoutingLogic(), routees)
//    Router(ScatterGatherFirstCompletedRoutingLogic(200 seconds), routees)
  }

  def receive = LoggingReceive {
    case AuctionSearch.Register(auction, title) =>
//      System.err.println(s"MASTER REGISTERING | $title")
      registerRouter.route(AuctionSearch.Register(auction, title), sender())
    case AuctionSearch.Search(query) =>
//      System.err.println(s"MASTER SEARCHING | $query")
      searchRouter.route(AuctionSearch.Search(query), sender())
  }
}
