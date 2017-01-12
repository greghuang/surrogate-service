package com.trend.spn

import java.nio.file.Paths
import java.util.concurrent.TimeUnit


import akka.actor.{Props, Actor, ActorSystem}
import akka.kafka.{Subscriptions, ConsumerSettings, ProducerSettings}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.{Done, NotUsed}
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Keep, Framing, FileIO}
import akka.util.{Timeout, ByteString}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, ByteArrayDeserializer, StringSerializer, ByteArraySerializer}
import org.scalatest.{BeforeAndAfterEach}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.stream.{KillSwitches, ActorMaterializer}
import akka.stream.testkit.{TestSubscriber, StreamSpec}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

/**
 * Created by GregHuang on 1/9/17.
 */
class KafkaStreamSpec extends StreamSpec
    with BeforeAndAfterEach {

    val props =
        ConfigFactory.parseString( """
        akka.kafka.consumer.kafka-clients.bootstrap.servers = "localhost:9092"
        akka.kafka.consumer.topic = test01""")
        .withFallback(ConfigFactory.load())

    implicit val config = props.getConfig("akka.kafka.consumer")
    implicit val mat = ActorMaterializer()(system)
    implicit val ec = system.dispatcher
    implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(9092, 2181)
    val topic = config.getString("topic")
    val partition = 0
    val groupID = "testgroup_0"
    val InitialMsg = "initial msg in topic, required to create the topic before any consumer subscribes to it"

    val bootstrapServers = config.getString("kafka-clients.bootstrap.servers")
    val producerSettings =
        ProducerSettings(system, new ByteArraySerializer, new StringSerializer).withBootstrapServers(bootstrapServers)

    def createFileProducer(path: String): Unit = {
        val producer = producerSettings.createKafkaProducer()
        producer.send(new ProducerRecord(topic, partition, null: Array[Byte], InitialMsg))
        producer.close(60, TimeUnit.SECONDS)

        val file = Paths.get(getClass.getResource(path).getPath)

        val source = FileIO.fromPath(file).
                via(Framing.delimiter(ByteString(System.lineSeparator), maximumFrameLength = 1024, allowTruncation = false)) .
                map(_.utf8String).
                map(n => {
                    val record = new ProducerRecord(topic, 0, null:Array[Byte], n)
                    Message(record, NotUsed)
                }).viaMat(Producer.flow(producerSettings))(Keep.right)

        val result: Future[Done] = source.runWith(Sink.ignore)
        Await.result(result, remainingOrDefault)
    }

    def createReferenceConsumer(topic: String)
    : TestSubscriber.Probe[String] = {
        val setting = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
                .withBootstrapServers(bootstrapServers)
                .withGroupId(groupID)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .withWakeupTimeout(10.seconds)
                .withMaxWakeups(10)

        val subscription = Subscriptions.assignmentWithOffset(
            new TopicPartition(topic, partition) -> 0L)


        Consumer.plainSource(setting, subscription)
                .filterNot(_.value == InitialMsg)
                .map { n =>
                    //println(n.value)
                    n.value
                }.runWith(TestSink.probe)
    }

    class Aggregator extends Actor {
        var count = 0
        def receive = {
            case log: String =>
                println("count:"+ count)
                count += 1
                sender() ! count // reply to the ask
        }
    }

    override protected def beforeEach(): Unit = {
        EmbeddedKafka.start()
        createFileProducer("/ad_proc_100_test.txt")
    }

    override def afterEach(): Unit = {
        EmbeddedKafka.stop()
    }

    "A Default Reactive Kafka consumer" must {
        "consume from a topic" in {
            val probe = createReferenceConsumer(topic)
            probe
            .request(100)
            .expectNextN(100)
            probe.cancel
        }
    }

    "An ADReactiveStream" must {
        "consume the specified topic" in {
            val adStream = ADReactiveStream(system, props)
            val probe: TestSubscriber.Probe[String] = adStream.getKafkaConsumer(topic, partition).runWith(TestSink.probe)

            probe.request(100).expectNextN(100)
            probe.cancel
        }
    }

    "An ADReactiveStream" must {
        "delegate log to an actor" in {
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

            system.scheduler.scheduleOnce(5.seconds) {
                println("Shutting down...")
                killSwitch.shutdown()
            }

            Await.result(result, 10.seconds)
            assert(actRef.count == 101)
            ref.stop()
        }
    }
}
