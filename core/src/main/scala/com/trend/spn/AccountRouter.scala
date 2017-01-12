package com.trend.spn

import akka.actor.Actor.Receive
import akka.actor.{Actor, Props}
import akka.routing.{RoundRobinRoutingLogic, ActorRefRoutee, Router}

/**
 * Created by GregHuang on 1/9/17.
 */

sealed trait RouterCommand
case object InitCommand extends RouterCommand
case object AckCommand extends RouterCommand
case object CompleteCommand extends RouterCommand

object AccountRouter {
    def props(): Props = Props(new AccountRouter())
}

class AccountRouter extends Actor {
    var router: Router = _

//    router = {
//        val routees = Vector.fill(props.streamParallelism) {
//            val r = context.actorOf(Props(classOf[InterceptorActor], props))
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
