package lab2.auctions

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props, Scheduler}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


object Buyer {
  case class Bid(auction: ActorRef, amount: BigInt)
  case object Win

  def props(auctions: List[ActorRef]): Props = Props(new Buyer(auctions))
}

class Buyer(auctions: List[ActorRef]) extends Actor {
  def receive = LoggingReceive {
    case Buyer.Bid(auction, amount) => auction ! Auction.Bid(amount)
    case Auction.BidSuccess => println(s"Bid success: $self -> $sender")
    case Auction.BidFailed => println(s"Bid failed: $self -> $sender")
    case Buyer.Win => println(s"Won auction: $self -> $sender")
  }
}

object AuctionState extends Enumeration {
  type AuctionState = Value
  val Created, Activated, Sold, Ignored, Expired = Value
}

object Auction {
  case class Bid(amount: BigInt) {
    require(amount > 0)
  }
  case object BidSuccess
  case object BidFailed

  def props(scheduler: Scheduler, bidTime: FiniteDuration, deleteTime: FiniteDuration): Props =
    Props(new Auction(scheduler, bidTime, deleteTime))
}

class Auction(scheduler: Scheduler, bidTime: FiniteDuration, deleteTime: FiniteDuration) extends Actor {
  import Auction._

  case object BidTimeExpired
  case object DeleteTimeExpired

  var state = AuctionState.Created
  var currentBid: BigInt = 0
  var currentBuyer: ActorRef = _

  var bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
  var deleteTimer: Cancellable = _

  def isActive: Boolean = {
    state == AuctionState.Created || state == AuctionState.Activated
  }

  def receive = LoggingReceive {
    case Bid(amount) if isActive =>
      bidTimer.cancel()
      if (amount > currentBid) {
        currentBid = amount
        currentBuyer = sender
        sender ! BidSuccess
      } else {
        sender ! BidFailed
      }
      bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
      state = AuctionState.Activated
    case Bid(amount) if state == AuctionState.Ignored =>
      deleteTimer.cancel()
      currentBid = amount
      currentBuyer = sender
      state = AuctionState.Activated
      sender ! BidSuccess
      bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
    case BidTimeExpired if state == AuctionState.Created =>
      state = AuctionState.Ignored
      deleteTimer = scheduler.scheduleOnce(deleteTime, self, DeleteTimeExpired)
    case BidTimeExpired if state == AuctionState.Activated =>
      state = AuctionState.Sold
      deleteTimer = scheduler.scheduleOnce(deleteTime, self, DeleteTimeExpired)
      currentBuyer ! Buyer.Win
    case DeleteTimeExpired =>
      state = AuctionState.Expired
      context.parent ! AuctionState.Expired
  }
}

object AuctionSystem {
  case object Init

  def props(scheduler: Scheduler): Props = Props(new AuctionSystem(scheduler))
}

class AuctionSystem(scheduler: Scheduler) extends Actor {
  import AuctionSystem._

  var auctions: ArrayBuffer[ActorRef] = {
    val a = new mutable.ArrayBuffer[ActorRef]()
    (1 to 10).foreach(i => {
      a += context.actorOf(Auction.props(scheduler, 10 seconds, 10 seconds), s"auction$i")
    })
    a
  }
  var buyers: ArrayBuffer[ActorRef] = {
    val a = new mutable.ArrayBuffer[ActorRef]()
    (1 to 3).foreach(i => {
      a += context.actorOf(Buyer.props(auctions.toList), s"buyer$i")
    })
    a
  }

  def receive = LoggingReceive {
    case Init => {
      val r = scala.util.Random
      (1 to 10).foreach(i => {
        buyers.foreach(b => {
          b ! Buyer.Bid(auctions(r.nextInt(auctions.length)), r.nextInt(100) + 1)
          Thread sleep 200
        })
      })
    }
    case AuctionState.Expired => {
      println(s"Expired: $sender")
      auctions.remove(auctions.indexOf(sender))
      if (auctions.isEmpty) {
        System.exit(0)
      }
    }
  }
}

object AuctionSystemApp extends App {
  val system = ActorSystem("AuctionSystem")
  val mainActor = system.actorOf(AuctionSystem.props(system.scheduler), "mainActor")
  mainActor ! AuctionSystem.Init
  system.awaitTermination()
}
