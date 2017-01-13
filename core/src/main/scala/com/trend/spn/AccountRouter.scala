package com.trend.spn

import akka.actor.Actor.Receive
import akka.actor.{Actor, Props}
import akka.routing.{RoundRobinRoutingLogic, ActorRefRoutee, Router}
import com.typesafe.config.Config

/**
 * Created by GregHuang on 1/9/17.
 */

sealed trait RouterCommand

case object InitCommand extends RouterCommand

case object AckCommand extends RouterCommand

case object CompleteCommand extends RouterCommand

object AccountRouter {
    def props(routerConfig: Config): Props = Props(new AccountRouter(routerConfig))
}

class AccountRouter(routerConfig: Config) extends Actor {
    var router: Router = _
    var partition = 5

//    router = {
//        val routees = Vector.fill(partition) {
//            val r = context.actorOf(Props(classOf[Account], props))
//            context watch r
//            ActorRefRoutee(r)
//        }
//        Router(RoundRobinRoutingLogic(), routees)
//    }

    override def receive: Receive = {
        case InitCommand => sender() ! AckCommand
        case CompleteCommand => println("Complete!!")
        case _ => println("Do nothing")
    }
}
