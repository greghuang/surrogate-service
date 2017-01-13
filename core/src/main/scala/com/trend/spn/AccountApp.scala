package com.trend.spn

import akka.actor.{Props, ActorSystem}

/**
 * Created by GregHuang on 12/30/16.
 */
object AccountApp extends App {
  val system = ActorSystem("AccountExample")
  val accountActor = system.actorOf(Props[Account])
  accountActor ! Event(Evt4769("greghuang"))

  Thread.sleep(1000)
  system.terminate()
}
