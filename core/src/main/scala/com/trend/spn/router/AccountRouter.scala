package com.trend.spn.router

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorRef, Actor, Props}
import akka.routing.ConsistentHashingRouter.{ConsistentHashMapping, ConsistentHashable}
import akka.routing.{ConsistentHashingRoutingLogic, ConsistentHashingPool, ActorRefRoutee, Router}
import com.trend.spn.Account
import com.typesafe.config.Config

/**
 * Created by GregHuang on 1/9/17.
 */

sealed trait RouterCommand
case object InitCommand extends RouterCommand
case object AckCommand extends RouterCommand
case object CompleteCommand extends RouterCommand

/*
    To ensure the message sent to the same actor based on the same key
 */
final case class Message(key: String, log: String) extends ConsistentHashable {
    override def consistentHashKey: Any = key
}

object AccountRouter {
    def props(routerConfig: Config): Props = Props(new AccountRouter(routerConfig))
}

class AccountRouter(routerConfig: Config) extends Actor {
    val name = self.path.name
    var partition = 1
    var router: Router = _
    val counter = new AtomicLong(0)

//    var routerPool: ActorRef = context.system.actorOf(
//            ConsistentHashingPool(10).props(Account.props(routerConfig)),
//            name = s"${name}_Pool")

    def addRoutee(): ActorRefRoutee = {
        val name = s"Account-${counter.incrementAndGet()}"
        val r = context.actorOf(Account.props(routerConfig), name)
        context watch r
        ActorRefRoutee(r)
    }

    def createRouter: Router = {
        val routees = Vector.fill(3) {
            addRoutee()
        }
        // Set virtualNodesFactor = 1 in order to no partition
        Router(ConsistentHashingRoutingLogic(context.system, 10), routees)
    }

    override def postStop(): Unit = {
        println(s"${name} is in the end")
    }

    override def preStart(): Unit = {
        println(s"${name} is established")
        router = createRouter
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        context.children foreach { child =>
            context.unwatch(child)
            context.stop(child)
            router.removeRoutee(child)
        }
        postStop()
    }

    override def receive: Receive = {
        case InitCommand => sender() ! AckCommand
        case CompleteCommand => println("Complete!!")
        case msg: Message => {
            //println("Receive a message:" + key)
            sender() ! 1
            router.route(msg, self)
        }
    }
}
