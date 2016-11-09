package lab4.auctions

import akka.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Props}
import akka.event.LoggingReceive
import lab4.auctions.actors.{Auction, AuctionSearch, Buyer, Seller}

import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


object AuctionSystem {
  case object Init
}

class AuctionSystem() extends Actor {

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

  val buyers: ArrayBuffer[ActorRef] = {
    val bs = new ArrayBuffer[ActorRef]()
    buyersMap.foreach { case (buyerId, queries) =>
      bs += context.actorOf(Buyer.props(search.path, 40), s"buyer$buyerId")
    }
    bs
  }

  val sellers: ArrayBuffer[ActorRef] = {
    val ss = new ArrayBuffer[ActorRef]()
    sellersMap.foreach { case (seller, auctions) =>
      val s = context.actorOf(Seller.props(search.path, 10 seconds, 10 seconds), seller)
      s ! Seller.CreateAuctions(auctions)
      ss += s
    }
    ss
  }

  def receive = LoggingReceive {
    case Init =>
      // we need to wait for the auctions to be created...
      Thread sleep 1000
      buyersMap.foreach { case (buyerId, queries) =>
        buyers(buyerId) ! Buyer.Buy(queries)
        Thread sleep 50
      }
    case Seller.SellerFinished =>
      println(s"SELLER FINISHED | ${sender.path.name}")
      sellers -= sender
      if (sellers.isEmpty) {
        println(s"ALL AUCTIONS FINISHED | Terminating system...")
        context.system.terminate()
      }
  }
}

object AuctionSystemApp extends App {
  val system = ActorSystem("AuctionSystem")
  val mainActor = system.actorOf(Props[AuctionSystem], "mainActor")
  mainActor ! AuctionSystem.Init
  Await.result(system.whenTerminated, Duration.Inf)
}
