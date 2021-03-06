package com.trend.spn

import akka.actor.Actor
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.Done
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.KillSwitches
import akka.stream.scaladsl._
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.stream.testkit.TestSubscriber
import com.trend.spn.router.{AccountRouter, Message}

/**
 * Created by GregHuang on 1/9/17.
 */

object ADReactiveStreamSpec {
    val config ="""
          akka.kafka.consumer{
            kafka-clients.bootstrap.servers = "localhost:9092"
            topic = test01
            sourcePath = /ad_proc_100_test.txt
          }
        """
}

class ADReactiveStreamSpec(props: Config) extends KafkaFileSource(props) {
    def this() = this(ConfigFactory.parseString(ADReactiveStreamSpec.config).withFallback(ConfigFactory.load()))

    class Aggregator extends Actor {
        var count = 0
        def receive = {
            case log: String => {
                count += 1
                println("count:" + count)
                sender() ! count // reply to the ask
            }
            case m: Message => {
                count += 1
                println("Msg count:" + count)
                sender() ! count // reply to the ask
            }
        }
    }

//    "A Default Reactive Kafka consumer" must {
//        "consume from a topic" in {
//            val probe = createReferenceConsumer(topic)
//            probe
//            .request(100)
//            .expectNextN(100)
//            probe.cancel
//        }
//    }
//
    "An ADReactiveStream" must {
        "consume the specified topic" in {
            implicit val timeout = Timeout(5.seconds)
            val adStream = ADReactiveStream(system, props)
            val source: Source[String, Consumer.Control] = adStream.getKafkaConsumer(topic, partition)
            val (control, probe) = source.toMat(TestSink.probe)(Keep.both).run

            probe.request(100).expectNextN(100)
            probe.cancel
            Await.result(control.shutdown(), 5 seconds)
        }

        "shutdown the consumer gracefully" in {
            implicit val timeout = Timeout(5 seconds)
            val adStream = ADReactiveStream(system, props)
            val g: RunnableGraph[(Consumer.Control, TestSubscriber.Probe[String])] =
                adStream.getKafkaConsumer(topic, partition).toMat(TestSink.probe)(Keep.both)
            val (control, probe) = g.run()
            probe.request(100).expectNextN(100)
            Await.result(control.shutdown(), 5 seconds)
            val isShutdown = Await.result(control.isShutdown, 5 seconds)
            assert( isShutdown.isInstanceOf[Done] )
        }
    }

    "An ADReactiveStream" must {
        "delegate log to an actor bt getStreamWithActor" in {
            implicit val timeout = Timeout(5.seconds)
            implicit val ec = system.dispatcher
            val adStream = ADReactiveStream(system, props)
            val ref = TestActorRef(new Aggregator)
            val actRef = ref.underlyingActor
            //val probe: TestSubscriber.Probe[Int] = adStream.getStreamWithActor(topic, partition, ref).runWith(TestSink.probe)

            val (killSwitch, result) = adStream.getStreamWithActor(topic, partition, ref)
                    .viaMat(KillSwitches.single)(Keep.right)
                    .toMat(Sink.ignore)(Keep.both)
                    .run()

            system.scheduler.scheduleOnce(3 seconds) {
                println("Shutting down...")
                killSwitch.shutdown()
            }

            Await.result(result, 5 seconds)
            assert(actRef.count == 101)
            ref.stop()
        }

        "build-in graph behavor as expected" in {
            implicit val timeout = Timeout(5 seconds)
            implicit val ec = system.dispatcher
            val adStream = ADReactiveStream(system, props)
            val ref = TestActorRef(new Aggregator)
            val actRef = ref.underlyingActor

            val resSink = Sink.last[Int]
            val (control, temp, kill, result) = adStream.accountMatrixGraph(ref, topic, partition).run()

            system.scheduler.scheduleOnce(3 seconds) {
                println("Shutting down...")
                kill.shutdown()
            }

            Await.result(result, 5 seconds)
            ref.underlyingActor.count shouldBe(101)
            ref.stop()
        }

        "run the graph with specified AccountRouter actor" in {
            implicit val timeout = Timeout(5.seconds)
            val router = system.actorOf(AccountRouter.props(props), "testRouter")
            val adStream = ADReactiveStream(system, props)
            val g = adStream.accountMatrixGraph(router, topic, partition)
            val (control, temp, kill, result) = g.run()

            system.scheduler.scheduleOnce(3 seconds) {
                println("Shutting down...")
                kill.shutdown()
            }
            Await.result(result, 5 seconds)
        }
    }
}
