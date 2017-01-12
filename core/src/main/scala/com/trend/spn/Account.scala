package com.trend.spn

import akka.actor.Props
import akka.persistence._

import scala.collection.mutable.Map
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer

/**
 * Created by GregHuang on 12/30/16.
 */
sealed abstract class WinEvt
case class Evt4769(host: String, id: String = "4769") extends WinEvt
case class Evt4768(host: String, id: String = "4768") extends WinEvt
case class Evt4624(host: String, id: String = "4624") extends WinEvt
case class Evt4625(host: String, id: String = "4625") extends WinEvt

case class Event(data: WinEvt)

sealed trait Query
case object LastHostQuery extends Query
case object HostListQuery extends Query
case object ListTop3Hosts extends Query

case class Top3Host(host1: String, host2: String, host3: String)

class Stat {
  val hosts: Map[String, Int] = new HashMap[String, Int]()
  var lastHost: String = _
  def update(evt: Event): Unit = {
    updateByEventID(evt.data)
  }
  def updateByEventID(evt: WinEvt): Unit = evt match {
    case Evt4624(host, id) => {
      println("This is 4624")
      updateHostList(host)
    }
    case Evt4625(host, id) => println("This is 4625")
    case Evt4768(host, id) => println("This is 4768")
    case Evt4769(host, id) => {
      println("This is 4769")
      lastHost = host
    }
  }
  def updateHostList(host: String): Unit = {
    val newVal = hosts.getOrElse(host, 0) + 1
    hosts += host -> newVal
    lastHost = host
  }
  def getTop3Hosts(): Top3Host = {
    Top3Host("foo", "bar", "wow")
  }
}

object Account {
  def props(name: String): Props = Props(new Account(name))
}

class Account(name: String) extends PersistentActor {
  import context._
  val accountName = name
  var stat = new Stat
  var events = new ListBuffer[WinEvt]()
  var lastSnapshot: SnapshotMetadata = _

  override def receiveRecover: Receive = {
    case evt: Event => stat.update(evt)
    case SnapshotOffer(metadata, offeredSnapshot: Stat) => {
      lastSnapshot = metadata
      stat = offeredSnapshot
    }
  }

  override def receiveCommand: Receive = {
    case Event(data) => {
      events += data
      persist(Event(data))(stat.update)
    }
    case LastHostQuery => sender ! stat.lastHost
    case HostListQuery => sender ! stat.hosts.toString
    case ListTop3Hosts =>
    case _ => akka.actor.Status.Failure(new RuntimeException("Unknown command"))
  }

  override def persistenceId: String = s"$name"
}
