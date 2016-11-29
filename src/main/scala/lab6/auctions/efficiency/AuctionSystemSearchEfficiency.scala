package lab6.auctions.efficiency

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import lab6.auctions.AuctionPublisher
import lab6.auctions.actors._

import scala.collection.GenIterable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random


object AuctionSystem {

  val SYSTEM = "AuctionSystem"
  val ACTOR = "mainActor"
  val CONFIG = "auctionsystem"

  case object Init

  def props(publisherPath: String, searchInstsCount: Int): Props = Props(new AuctionSystem(publisherPath, searchInstsCount))
}

class AuctionSystem(publisherPath: String, searchInstsCount: Int) extends Actor {

  import AuctionSystem._

  val search = context.actorOf(MasterSearch.props(searchInstsCount), "search")
  val notifier = context.actorOf(Notifier.props(publisherPath), "notifier")

  implicit val timeout = Timeout(20 seconds)
  val creator = context.actorOf(Props[Creator], "creator")

  def receive = LoggingReceive {
    case Init =>
      val createdFuture = creator ? Creator.CreateAuctions(10 seconds, 10 seconds, notifier.path.toStringWithoutAddress, search.actorRef)
      val createdResult = Await.result(createdFuture, timeout.duration)
      creator ! Creator.QueryAuctions(search)
    case Creator.QueryingFinished(duration) =>
      System.err.println(s"$duration")
      context.system.terminate()
  }
}

object Creator {
  case object AuctionsCreated
  case class QueryingFinished(duration: FiniteDuration)
  case class CreateAuctions(bidTime: FiniteDuration, deleteTime: FiniteDuration, notifierPath: String, search: ActorRef)
  case class QueryAuctions(search: ActorRef)
}

class Creator extends Actor {
  import Creator._

  implicit val timeout = Timeout(100 seconds)

  var finishedSearches = 0
  var failed = 0
  val searches = 1000
  val auctions = 5000
  var startTime = System.currentTimeMillis()

  def receive = LoggingReceive {
    case CreateAuctions(bidTime, deleteTime, notifierPath, search) =>
      (1 to auctions).foreach(i => {
        val name: String = "a_" + i
        val auction = context.actorOf(Auction.props(name, name, bidTime, deleteTime, notifierPath), name)
        search ! AuctionSearch.Register(auction, name)
      })
      sender ! AuctionsCreated
    case QueryAuctions(search) =>
      val r = new Random()
      val shuffled: GenIterable[Int] = r.shuffle((1 to searches).toList)
      startTime = System.currentTimeMillis()
      shuffled.foreach(i => {
        search ! AuctionSearch.Search(i.toString)
        // ScatterGatherFirstCompleted
//        val future = search ? AuctionSearch.Search(i.toString)
//        val result = Await.result(future, timeout.duration)
//        checkResult()
      })
    case AuctionSearch.SearchResult(q, res) =>
      System.err.println("RESULT | " + q)
      checkResult()
    case Failure(e: AskTimeoutException) =>
      failed += 1
      checkResult()
  }

  def checkResult(): Unit = {
    finishedSearches += 1
    if (finishedSearches >= searches) {
      val duration: FiniteDuration = ((System.currentTimeMillis() - startTime) millis)
      finishedSearches = 0
      failed = 0
      System.err.println("FAILED: " + failed)
      context.parent ! Creator.QueryingFinished(duration)
    }
  }
}

object AuctionSystemApp extends App {
  val config = ConfigFactory.load()

  val searchInstsCount = 4

  //  System.err.println("Instances: " + searchInstsCount)

  val publisherPath = "akka.tcp://%s@%s/user/%s".format(AuctionPublisher.SYSTEM, AuctionPublisher.HOSTPORT, AuctionPublisher.ACTOR)
  val auctionSystem = ActorSystem(AuctionSystem.SYSTEM, config.getConfig(AuctionSystem.CONFIG).withFallback(config))
  val auctionSystemActor = auctionSystem.actorOf(AuctionSystem.props(publisherPath, searchInstsCount), AuctionSystem.ACTOR)

  auctionSystemActor ! AuctionSystem.Init

  Await.result(auctionSystem.whenTerminated, Duration.Inf)
}
