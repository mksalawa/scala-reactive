package lab5.auctions

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive
import com.typesafe.config.ConfigFactory
import lab5.auctions.actors._

import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._


object AuctionSystem {
  case object Init

  def props(publisherPath: String): Props = Props(new AuctionSystem(publisherPath))
}

class AuctionSystem(publisherPath: String) extends Actor {

  import AuctionSystem._

  private val sellersMap = HashMap[String, List[String]](
    "seller1" -> List("Neksus 6 new phone", "Phone case Sansung S34", "MyPhone 7 best price"),
    "seller2" -> List("Proshe 911 2012 350hp", "Gaudi G6 2014 eco", "Thesla 5 2015 electric"),
    "seller3" -> List("Bed sheets floral, various colours", "Pillow 30x30, soft & fluffy")
  )

  private val buyersMap = HashMap[Int, List[String]](
    0 -> List("Neksus", "Pillow"),
    1 -> List("Proshe", "Neksus", "Gaudi"),
    2 -> List("Proshe", "Pillow", "Bed"),
    3 -> List("Sansung", "Thesla", "Gaudi"),
    4 -> List("Sansung", "MyPhone", "Thesla")
  )

  val search = context.actorOf(Props[AuctionSearch], "search")
  val notifier = context.actorOf(Notifier.props(publisherPath), "notifier")

  val buyers: ArrayBuffer[String] = {
    val bs = new ArrayBuffer[String]()
    buyersMap.foreach { case (buyerId, queries) =>
      val b = context.actorOf(Buyer.props(search.path.toStringWithoutAddress, 50), s"buyer$buyerId")
      bs += b.path.toStringWithoutAddress
    }
    bs
  }

  val sellers: ArrayBuffer[String] = {
    val ss = new ArrayBuffer[String]()
    sellersMap.foreach { case (seller, auctions) =>
      val s = context.actorOf(Seller.props(search.path.toStringWithoutAddress, notifier.path.toStringWithoutAddress, 20 seconds, 10 seconds), seller)
      s ! Seller.CreateAuctions(auctions)
      ss += s.path.toStringWithoutAddress
    }
    ss
  }

  def receive = LoggingReceive {
    case Init =>
      // we need to wait for the auctions to be created...
      Thread sleep 1000
      buyersMap.foreach { case (buyerId, queries) =>
        context.actorSelection(buyers(buyerId)) ! Buyer.Buy(queries)
        Thread sleep 50
      }
    case Seller.SellerFinished =>
      println(s"SELLER FINISHED | ${sender.path.name}")
      sellers -= sender.path.toStringWithoutAddress
      if (sellers.isEmpty) {
        println(s"ALL AUCTIONS FINISHED | Terminating system...")
        context.system.terminate()
      }
  }
}

object AuctionSystemApp extends App {
  val config = ConfigFactory.load()

  val auctionSystem = ActorSystem("AuctionSystem", config.getConfig("auctionsystem").withFallback(config))
  val auctionSystemActor = auctionSystem.actorOf(AuctionSystem.props("akka.tcp://AuctionPublisherSystem@127.0.0.1:2553/user/auctionPublisherActor"), "mainActor")

  auctionSystemActor ! AuctionSystem.Init

  Await.result(auctionSystem.whenTerminated, Duration.Inf)
}
