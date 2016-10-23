package lab3.auctions.actors

import akka.actor.{Actor, ActorRef, FSM, Props}
import scala.concurrent.duration.FiniteDuration


object Auction {
  case class BidSuccess(auction: ActorRef)
  case class BidFailed(auction: ActorRef)
  case class Win(auction: ActorRef)

  def props(title: String, bidTime: FiniteDuration, deleteTime: FiniteDuration): Props =
    Props(new Auction(title, bidTime, deleteTime))
}

class Auction(title: String, bidTime: FiniteDuration, deleteTime: FiniteDuration) extends Actor with FSM[State, Data] {

  startWith(Created, AuctionData(title, 0, null))

  case object BidTimeExpired

  case object DeleteTimeExpired

  when(Created, stateTimeout = bidTime) {
    case Event(Bid(amount, buyer), data) =>
      buyer ! Auction.BidSuccess(self)
      goto(Activated) using AuctionData(title, amount, buyer)
    case Event(StateTimeout, data) =>
      goto(Ignored)
  }

  when(Ignored, stateTimeout = deleteTime) {
    case Event(Bid(amount, buyer), data) =>
      buyer ! Auction.BidSuccess(self)
      goto(Activated) using AuctionData(title, amount, buyer)
    case Event(StateTimeout, data) =>
      context.parent ! Expired
      goto(Expired)
  }

  when(Activated, stateTimeout = bidTime) {
    case Event(Bid(amount, buyer), AuctionData(t, currAmount, currBuyer)) if amount > currAmount =>
      buyer ! Auction.BidSuccess(self)
      stay using AuctionData(t, amount, buyer)
    case Event(Bid(amount, buyer), AuctionData(t, currAmount, currBuyer)) =>
      buyer ! Auction.BidFailed(self)
      stay
    case Event(StateTimeout, AuctionData(t, currAmount, currBuyer)) =>
      goto(Sold) using AuctionData(t, currAmount, currBuyer)
  }

  when(Sold, stateTimeout = deleteTime) {
    case Event(StateTimeout, data) =>
      context.parent ! data
      goto(Expired)
  }

  when(Expired) {
    case _ => stay
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
case class AuctionData(title: String, currentBid: BigInt, currentBuyer: ActorRef) extends Data
case class Bid(amount: BigInt, buyer: ActorRef) extends Data {
  require(amount > 0)
}
