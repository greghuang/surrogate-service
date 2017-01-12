package com.trend.spn

import akka.actor.ActorSystem

/**
 * Created by GregHuang on 12/30/16.
 */
object AccountApp extends App {
    val system = ActorSystem("AccountExample")
    val accountActor = system.actorOf(Account.props("Greg"))
    accountActor ! Event(Evt4769("greghuang"))

    Thread.sleep(1000)
    system.terminate()
}
