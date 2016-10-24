package lab3.auctions

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import lab3.auctions.actors.{AuctionSearch, Buyer, Seller}

import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
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

  val search = context.actorOf(Props[AuctionSearch], "search")

  val buyers: ArrayBuffer[ActorRef] = {
    val bs = new ArrayBuffer[ActorRef]()
    (1 to 3).foreach(i => {
      bs += context.actorOf(Buyer.props(search.path, 20), s"buyer$i")
    })
    bs
  }

  val sellers: ArrayBuffer[ActorRef] = {
    val ss = new ArrayBuffer[ActorRef]()
    sellersMap.foreach { case (seller, auctions) =>
      val s = context.actorOf(Seller.props(search.path, 2 seconds, 2 seconds), seller)
      s ! Seller.CreateAuctions(auctions)
    }
    ss
  }

  def receive = LoggingReceive {
    case Init =>

      // we need to wait for the auctions to be created...
      Thread sleep 200
      buyers(0) ! Buyer.Buy(List("Neksus", "Pillow"))
      Thread sleep 50
      buyers(1) ! Buyer.Buy(List("Proshe", "Neksus"))
      Thread sleep 50
      buyers(2) ! Buyer.Buy(List("Proshe", "Pillow", "Bed"))
  }
}

object AuctionSystemApp extends App {
  val system = ActorSystem("AuctionSystem")
  val mainActor = system.actorOf(Props[AuctionSystem], "mainActor")
  mainActor ! AuctionSystem.Init
  Await.result(system.whenTerminated, Duration.Inf)
}
