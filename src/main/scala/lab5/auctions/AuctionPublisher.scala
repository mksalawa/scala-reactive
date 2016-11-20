package lab5.auctions

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive
import com.typesafe.config.ConfigFactory
import lab5.auctions.actors._

import scala.concurrent.Await
import scala.concurrent.duration._


class AuctionPublisher() extends Actor {

  def receive = LoggingReceive {
    case AuctionData(t, currBid, currBuyer, timeout) =>
      // System.err for visible differentiation of logs
      System.err.println(t + " | " + currBid + " | " + currBuyer + " | " + timeout)
      sender ! "ok"
  }
}

object AuctionPublisherApp extends App {
  val config = ConfigFactory.load()

  val publisherSystem = ActorSystem("AuctionPublisherSystem", config.getConfig("auctionpublisher").withFallback(config))
  val publisherActor = publisherSystem.actorOf(Props[AuctionPublisher], "auctionPublisherActor")

  Await.result(publisherSystem.whenTerminated, Duration.Inf)
}
