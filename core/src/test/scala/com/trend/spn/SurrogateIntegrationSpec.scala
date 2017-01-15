package com.trend.spn

import akka.actor.Actor
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.Done
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.KillSwitches
import akka.stream.scaladsl._
import akka.util.Timeout
import com.trend.spn.router.AccountRouter
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
/**
  * Created by greghuang on 14/01/2017.
  */
object SurrogateIntegrationSpec {
  val testConf =
    """
    akka.kafka.consumer {
      kafka-clients.bootstrap.servers = "localhost:9092"
      topic = test01
      sourcePath = /ad_proc_100_test.txt }
    """
}

class SurrogateIntegrationSpec(config: Config) extends KafkaFileSource(config) {
  def this() = this(IntegrationSpec.config("inmem", "SurrogateTest", "on", Some(SurrogateIntegrationSpec.testConf)))

  "Surrogate service" must {
    "consume the specified topic" in {
      implicit val timeout = Timeout(5.seconds)
      val adStream = ADReactiveStream(system, config)
      val source: Source[String, Consumer.Control] = adStream.getKafkaConsumer(topic, partition)
      val (control, probe) = source.toMat(TestSink.probe)(Keep.both).run

      probe.request(100).expectNextN(100)
      probe.cancel
      Await.result(control.shutdown(), 5 seconds)
    }

//    "run the graph with specified AccountRouter actor" in {
//      implicit val timeout = Timeout(5.seconds)
//      val router = system.actorOf(AccountRouter.props(config), "testRouter")
//      val adStream = ADReactiveStream(system, config)
//      val g = adStream.accountMatrixGraph(router, topic, partition)
//      val (control, temp, kill, result) = g.run()
//
//      system.scheduler.scheduleOnce(3 seconds) {
//        println("Shutting down...")
//        kill.shutdown()
//      }
//      Await.result(result, 5 seconds)
//    }
  }
}
