package lab3.auctions.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import lab3.auctions.actors.AuctionSearch.Register
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class AuctionSearchSpec extends TestKit(ActorSystem("AuctionSearchSpec"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender with Matchers {

  override def afterAll(): Unit = {
    system.terminate
  }

  "AuctionSearch" must {

    "return a list of auctions matching the query" in {
      val auction1 = TestProbe()
      val auction2 = TestProbe()
      val search = TestActorRef[AuctionSearch]
      search ! Register(auction1.ref, "first auction")
      search ! Register(auction2.ref, "second auction")

      search ! AuctionSearch.Search("first")
      expectMsg(AuctionSearch.SearchResult("first", List(auction1.ref)))

      search ! AuctionSearch.Search("auction")
      expectMsgPF() {
        case AuctionSearch.SearchResult("auction", results) =>
          results should contain theSameElementsAs List(auction1.ref, auction2.ref)
      }
    }

    "return an empty list if query did not match any auction" in {
      val auction1 = TestProbe()
      val search = TestActorRef[AuctionSearch]
      search ! Register(auction1.ref, "first auction")

      search ! AuctionSearch.Search("second")
      expectMsg(AuctionSearch.SearchResult("second", List.empty[ActorRef]))
    }
  }
}
