package lab3.auctions.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._


class SellerSpec extends TestKit(ActorSystem("SellerSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender with Matchers {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Seller" must {

    "create auctions and register them in Search" in {
      val search = TestProbe()
      val seller = TestActorRef(Seller.props(search.ref.path, 500 millis, 500 millis))
      seller ! Seller.CreateAuctions(List("a1", "a2"))

      search.expectMsgPF() {
        case AuctionSearch.Register(_, title) =>
          title should equal("a1")
      }
      search.expectMsgPF() {
        case AuctionSearch.Register(_, title) =>
          title should equal("a2")
      }
    }

    "notify buyer about winning the auction" in {
      val search = TestProbe()
      val buyerProbe = TestProbe()
      val seller = TestActorRef(Seller.props(search.ref.path, 500 millis, 500 millis))
      seller ! Seller.CreateAuctions(List("a1"))

      search.expectMsgPF() {
        case AuctionSearch.Register(a1Ref, "a1") =>
          a1Ref ! Bid(100, buyerProbe.ref)
          buyerProbe.expectMsg(Auction.BidSuccess(a1Ref))
          buyerProbe.expectMsg(Auction.Win(a1Ref))
      }
    }
  }
}
