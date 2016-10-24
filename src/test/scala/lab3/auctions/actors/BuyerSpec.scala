package lab3.auctions.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class BuyerSpec extends TestKit(ActorSystem("BuyerSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender with Matchers {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Buyer" must {

    "bid after getting BidRaised notification if has enough budget" in {
      val search = TestProbe()
      val auction1 = TestProbe()
      val buyer = TestActorRef(Buyer.props(search.ref.path, 500))
      buyer ! Buyer.Buy(List("a1"))
      search.expectMsg(AuctionSearch.Search("a1"))
      buyer ! AuctionSearch.SearchResult("a1", List(auction1.ref))

      var initBid: BigInt = 0
      auction1.expectMsgPF() {
        case Auction.Bid(amount, b) if b.eq(buyer) =>
          initBid = amount
      }

      buyer ! Auction.BidRaised(auction1.ref)

      auction1.expectMsgPF() {
        case Auction.Bid(amount, b) if b.eq(buyer) =>
          amount should be > initBid
      }
    }

    "not bid after getting BidRaised notification if does not have enough budget" in {
      val search = TestProbe()
      val auction1 = TestProbe()
      val buyer = TestActorRef(Buyer.props(search.ref.path, 2))
      buyer ! Buyer.Buy(List("a1"))
      search.expectMsg(AuctionSearch.Search("a1"))
      buyer ! AuctionSearch.SearchResult("a1", List(auction1.ref))
      var initBid: BigInt = 0
      auction1.expectMsgPF() {
        case Auction.Bid(amount, b) if b.eq(buyer) =>
          initBid = amount
      }

      buyer ! Auction.BidRaised(auction1.ref)

      auction1.expectNoMsg()
    }
  }
}
