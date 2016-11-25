package lab6.auctions.actors

import akka.actor.{ActorRef, Props}
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.persistence.fsm._
import lab6.auctions.actors.Auction._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.reflect._


object Auction {
  case class BidSuccess(auction: ActorRef)
  case class BidFailed(auction: ActorRef)
  case class BidRaised(auction: ActorRef)
  case class Win(auction: ActorRef)
  case class AuctionExpired(auctionData: AuctionData)
  case class Subscribe(listener: ActorRef)
  case class Bid(amount: BigInt, buyer: ActorRef) {
    require(amount > 0)
  }

  sealed trait AuctionEvent
  case class StateChangeEvent(data: AuctionData) extends AuctionEvent
  case class TickEvent(timeout: FiniteDuration) extends AuctionEvent

  def props(id: String, title: String, bidTime: FiniteDuration, deleteTime: FiniteDuration, notifierPath: String): Props =
    Props(new Auction(id, title, bidTime, deleteTime, notifierPath))
}

class Auction(id: String, title: String, bidTime: FiniteDuration, deleteTime: FiniteDuration, notifierPath: String)
  extends PersistentFSM [State, AuctionData, AuctionEvent] {


  case object ClockTick

  override def persistenceId = id
  override def domainEventClassTag: ClassTag[AuctionEvent] = classTag[AuctionEvent]

  private val TICK: FiniteDuration = (1000 millis)

  startWith(Created, AuctionData(title, 0, null, bidTime))
  setTimer("clock", ClockTick, TICK, repeat = true)

  whenUnhandled {
    case Event(ClockTick, AuctionData(t, currAmount, currBuyer, timeout)) =>
//      println(s"=> TICK | $title")
      val newTimeLeft: FiniteDuration = timeout - TICK
      if (newTimeLeft <= (0 millis)) {
        self ! StateTimeout
        stay applying TickEvent(deleteTime)
      } else {
        stay applying TickEvent(newTimeLeft)
      }
  }

  when(Created, stateTimeout = bidTime) {
    case Event(Bid(amount, buyer), _) =>
      println(s"BID OK! | $title | [state: Created] ${buyer.path.name} | $amount")
      buyer ! Auction.BidSuccess(self)
      goto(Activated) applying StateChangeEvent(AuctionData(title, amount, buyer.path.toStringWithoutAddress, bidTime))
    case Event(StateTimeout, _) =>
      println(s"BID TIMEOUT! | $title | [state: Created]")
      goto(Ignored)
  }

  when(Ignored, stateTimeout = deleteTime) {
    case Event(Bid(amount, buyer), _) =>
      println(s"BID OK! | $title | [state: Ignored] ${buyer.path.name} | $amount")
      buyer ! Auction.BidSuccess(self)
      goto(Activated) applying StateChangeEvent(AuctionData(title, amount, buyer.path.toStringWithoutAddress, bidTime))
    case Event(StateTimeout, data) =>
      println(s"DELETE TIMEOUT! | $title | [state: Ignored]")
      context.parent ! AuctionExpired(data)
      goto(Expired)
  }

  when(Activated, stateTimeout = bidTime) {
    case Event(Bid(amount, buyer), AuctionData(t, currAmount, currBuyer, timeout)) if amount > currAmount =>
      println(s"BID OK! | $title | [state: Activated] ${buyer.path.name} | $amount")
      buyer ! Auction.BidSuccess(self)
      context.actorSelection(currBuyer) ! BidRaised(self)
      stay applying StateChangeEvent(AuctionData(t, amount, buyer.path.toStringWithoutAddress, bidTime))
    case Event(Bid(amount, buyer), AuctionData(t, currAmount, currBuyer, timeout)) =>
      println(s"BID TOO LOW! | $title | [state: Activated] ${buyer.path.name} | $amount")
      buyer ! Auction.BidFailed(self)
      stay
    case Event(StateTimeout, AuctionData(t, currAmount, currBuyer, timeout)) =>
      println(s"BID TIMEOUT! | $title | [state: Activated]")
      goto(Sold) applying StateChangeEvent(AuctionData(t, currAmount, currBuyer, bidTime))
  }

  when(Sold, stateTimeout = deleteTime) {
    case Event(StateTimeout, data) =>
      println(s"DELETE TIMEOUT! | $title | [state: Sold]")
      context.parent ! data
      goto(Expired)
  }

  when(Expired) {
    case _ => stay
  }

  override def onRecoveryCompleted(): Unit = super.onRecoveryCompleted()

  override def applyEvent(event: AuctionEvent, dataBeforeEvent: AuctionData): AuctionData = {
    event match {
      case stateChangeEvent: StateChangeEvent =>
        val data = stateChangeEvent.data
        var buyer = "null"
        if (dataBeforeEvent.currentBuyer != null) {
          buyer = dataBeforeEvent.currentBuyer
        }
        context.actorSelection(notifierPath) ! Notifier.Notify(data)
        println(s"CHANGE $title \n\tFROM: ${dataBeforeEvent.currentBid} | $buyer | ${dataBeforeEvent.timeout} \n\tTO: " +
          s"${data.currentBid} | ${data.currentBuyer} | ${data.timeout}")
        data
      case clockTickEvent: TickEvent =>
        AuctionData(dataBeforeEvent.title, dataBeforeEvent.currentBid, dataBeforeEvent.currentBuyer, clockTickEvent.timeout)
    }
  }
}

// States of FSM
sealed trait State extends FSMState
case object Created extends State {
  override def identifier: String = "Created"
}
case object Ignored extends State {
  override def identifier: String = "Ignored"
}
case object Activated extends State {
  override def identifier: String = "Activated"
}
case object Sold extends State {
  override def identifier: String = "Sold"
}
case object Expired extends State {
  override def identifier: String = "Expired"
}

// Data that may be retained within FSM
sealed trait Data
case class AuctionData(title: String, currentBid: BigInt, currentBuyer: String, timeout: FiniteDuration) extends Data
