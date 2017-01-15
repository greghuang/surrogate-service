package com.trend.spn.router

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
    var partition = 1
    var router: Router = _
    var routerPool: ActorRef = context.system.actorOf(
            ConsistentHashingPool(10).props(Account.props(routerConfig)),
            name = "account-router")

//    def addRoutee(accountName: String): ActorRefRoutee = {
//        val r = context.actorOf(Account.props(accountName))
//        context watch r
//        ActorRefRoutee(r)
//    }

    def createRouter: Router = {
        val routees = Vector.empty
        Router(ConsistentHashingRoutingLogic(context.system), routees)
    }

    override def postStop(): Unit = {
        println("The AccountRouter is in the end")
    }

    override def preStart(): Unit = {
        println("A company is established")
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
            println("Receive a message")
            sender() ! 1
            //router.route(msg, self)
        }
    }
}
