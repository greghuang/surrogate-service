package com.trend.spn

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.persistence.PersistenceSpec

import akka.testkit.{TestActors, ImplicitSender, TestActorRef, TestKit}

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.util.Timeout

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


/**
 * Created by GregHuang on 12/30/16.
 */
class AccountSpec extends PersistenceSpec(PersistenceSpec.config("inmem", "AccountSpec")) with ImplicitSender {

    "An account" must {
        "receive the event and update the last host" in {
            // TestActorRef it will not work with some support traits that Akka provides as they require
            // asynchronous behaviours to function properly. Examples of traits that do not mix well with
            // test actor refs are PersistentActor and AtLeastOnceDelivery provided by Akka Persistence.
            //val accountRef = TestActorRef[Account](Account.props("Greg"))
            val accountRef = system.actorOf(Props(classOf[Account], "Greg"))

            accountRef ! Event(Evt4769("greghuang"))

//            implicit val timeout = Timeout(10.seconds)
//            val future = accountRef ? LastHostQuery
//            val result = Await.result(future, Duration.Inf).asInstanceOf[String]
//            result should be("greghuang")

            accountRef ! LastHostQuery
            expectMsg(3.seconds, "greghuang")
        }

        "receive events and update the host list" in {
            val accountRef = system.actorOf(Props(classOf[Account], "Greg"))

            accountRef ! Event(Evt4624("greghuang-01"))
            accountRef ! Event(Evt4624("greghuang-01"))
            accountRef ! Event(Evt4624("greghuang-02"))
            accountRef ! Event(Evt4624("greghuang-02"))
            accountRef ! Event(Evt4624("greghuang-03"))

            implicit val timeout = Timeout(10.seconds)
            val future = accountRef ? HostListQuery
            val result = Await.result(future, Duration.Inf).asInstanceOf[String]
            result should be("greghuang")
        }
    }
}
