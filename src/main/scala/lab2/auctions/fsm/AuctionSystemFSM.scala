package lab2.auctions.fsm

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, FSM, Props, Scheduler}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object Auction {
  case object BidSuccess
  case object BidFailed
  case object Win

  def props(scheduler: Scheduler, bidTime: FiniteDuration, deleteTime: FiniteDuration): Props =
    Props(new Auction(scheduler, bidTime, deleteTime))
}

class Auction(scheduler: Scheduler, bidTime: FiniteDuration, deleteTime: FiniteDuration) extends Actor with FSM[State, Data] {

  startWith(Created, EmptyData)
  initialize()

  case object BidTimeExpired

  case object DeleteTimeExpired

  var bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
  var deleteTimer: Cancellable = _

  when(Created) {
    case Event(Bid(amount, buyer), EmptyData) =>
      bidTimer.cancel()
      buyer ! Auction.BidSuccess
      bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
      goto(Activated) using AuctionData(amount, buyer)
    case Event(BidTimeExpired, data) =>
      deleteTimer = scheduler.scheduleOnce(deleteTime, self, DeleteTimeExpired)
      goto(Ignored)
    case Event(DeleteTimeExpired, data) =>
      goto(Expired)
  }

  when(Ignored) {
    case Event(Bid(amount, buyer), data) =>
      deleteTimer.cancel()
      buyer ! Auction.BidSuccess
      bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
      goto(Activated) using AuctionData(amount, buyer)
    case Event(DeleteTimeExpired, data) =>
      context.parent ! Expired
      goto(Expired)
  }

  when(Activated) {
    case Event(Bid(amount, buyer), AuctionData(currAmount, currBuyer)) if amount > currAmount =>
      bidTimer.cancel()
      buyer ! Auction.BidSuccess
      bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
      stay using AuctionData(amount, buyer)
    case Event(Bid(amount, buyer), AuctionData(currAmount, currBuyer)) =>
      bidTimer.cancel()
      buyer ! Auction.BidFailed
      bidTimer = scheduler.scheduleOnce(bidTime, self, BidTimeExpired)
      stay
    case Event(BidTimeExpired, AuctionData(currAmount, currBuyer)) =>
      currBuyer ! Auction.Win
      deleteTimer = scheduler.scheduleOnce(deleteTime, self, DeleteTimeExpired)
      goto(Sold)
  }

  when(Sold) {
    case Event(DeleteTimeExpired, data) =>
      context.parent ! Expired
      goto(Expired)
  }

  when(Expired) {
    case _ =>
      stay
  }
}

// States of FSM
sealed trait State
case object Created extends State
case object Ignored extends State
case object Activated extends State
case object Sold extends State
case object Expired extends State

// Data that may be retained within FSM
sealed trait Data
case object EmptyData extends Data
case class AuctionData(currentBid: BigInt, currentBuyer: ActorRef) extends Data
case class Bid(amount: BigInt, buyer: ActorRef) extends Data {
  require(amount > 0)
}


// Buyer and Auction system
object Buyer {
  case class Bid(auction: ActorRef, amount: BigInt)

  def props(auctions: List[ActorRef]): Props = Props(new Buyer(auctions))
}

class Buyer(auctions: List[ActorRef]) extends Actor {
  def receive = LoggingReceive {
    case Buyer.Bid(auction, amount) => auction ! Bid(amount, self)
    case Auction.BidSuccess => println(s"Bid success: $self -> $sender")
    case Auction.BidFailed => println(s"Bid failed: $self -> $sender")
    case Auction.Win => println(s"Won auction: $self -> $sender")
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
    case Init =>
      val r = scala.util.Random
      (1 to 10).foreach(i => {
        buyers.foreach(b => {
          b ! Buyer.Bid(auctions(r.nextInt(auctions.length)), r.nextInt(100) + 1)
          Thread sleep 200
        })
      })
    case Expired =>
      println(s"Expired: $sender")
      auctions.remove(auctions.indexOf(sender))
      if (auctions.isEmpty) {
        System.exit(0)
      }
  }
}

object AuctionSystemApp extends App {
  val system = ActorSystem("AuctionSystem")
  val mainActor = system.actorOf(AuctionSystem.props(system.scheduler), "mainActor")
  mainActor ! AuctionSystem.Init
  system.awaitTermination()
}
