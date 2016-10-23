package lab3.auctions.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

class AuctionSpec extends TestKit(ActorSystem("AuctionSpec"))
  with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Auction" must {

    "accept any proper first bid" in {
      val auction = TestActorRef(Props(new Auction("a1", 1 second, 1 second)))
      val buyerProbe = TestProbe()

      auction ! Bid(1, buyerProbe.ref)
      buyerProbe.expectMsg(Auction.BidSuccess(auction))
    }

    "accept higher than current bid" in {
      val auction = TestActorRef(Props(new Auction("a1", 1 second, 1 second)))
      val buyerProbe1 = TestProbe()
      val buyerProbe2 = TestProbe()
      auction ! Bid(1, buyerProbe1.ref)
      buyerProbe1.expectMsg(Auction.BidSuccess(auction))

      auction ! Bid(2, buyerProbe2.ref)
      buyerProbe2.expectMsg(Auction.BidSuccess(auction))
    }

    "not accept lower than current bid" in {
      val auction = TestActorRef(Props(new Auction("a1", 1 second, 1 second)))
      val buyerProbe1 = TestProbe()
      val buyerProbe2 = TestProbe()
      auction ! Bid(2, buyerProbe1.ref)
      buyerProbe1.expectMsg(Auction.BidSuccess(auction))

      auction ! Bid(1, buyerProbe2.ref)
      buyerProbe2.expectMsg(Auction.BidFailed(auction))
    }

    "not accept equal bid" in {
      val auction = TestActorRef(Props(new Auction("a1", 1 seconds, 1 seconds)))
      val buyerProbe1 = TestProbe()
      val buyerProbe2 = TestProbe()
      auction ! Bid(2, buyerProbe1.ref)
      buyerProbe1.expectMsg(Auction.BidSuccess(auction))

      auction ! Bid(2, buyerProbe2.ref)
      buyerProbe2.expectMsg(Auction.BidFailed(auction))
    }

    "notify seller after being sold" in {
      val sellerProbe = TestProbe()
      val auction = sellerProbe.childActorOf(Props(new Auction("a1", 500 millis, 500 millis)))
      val buyerProbe = TestProbe()

      auction ! Bid(2, buyerProbe.ref)
      buyerProbe.expectMsg(Auction.BidSuccess(auction))
      sellerProbe.expectMsg(1300 millis, AuctionData("a1", 2, buyerProbe.ref))
    }
  }
}
