package lab6.auctions

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive
import com.typesafe.config.ConfigFactory
import lab6.auctions.actors._

import scala.concurrent.Await
import scala.concurrent.duration._


object AuctionPublisher {
  val SYSTEM = "AuctionPublisherSystem"
  val ACTOR = "auctionPublisherActor"
  val CONFIG = "auctionpublisher"
  val HOSTPORT = "127.0.0.1:2553"

  case object Stop
}

class AuctionPublisher() extends Actor {

  def receive = LoggingReceive {
    case AuctionData(t, currBid, currBuyer, timeout) =>
      // System.err for visible differentiation of logs
      System.err.println(t + " | " + currBid + " | " + currBuyer + " | " + timeout)
      sender ! "ok"
    case AuctionPublisher.Stop =>
      println("Terminating publisher...")
      context.system.terminate()
  }
}

object AuctionPublisherApp extends App {
  val config = ConfigFactory.load()

  val publisherSystem = ActorSystem(AuctionPublisher.SYSTEM, config.getConfig(AuctionPublisher.CONFIG).withFallback(config))
  val publisherActor = publisherSystem.actorOf(Props[AuctionPublisher], AuctionPublisher.ACTOR)

  Await.result(publisherSystem.whenTerminated, Duration.Inf)
}
