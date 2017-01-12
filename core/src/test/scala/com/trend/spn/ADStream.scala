package com.trend.spn

import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Created by GregHuang on 12/15/16.
 */
object ADStream extends App {
    val config = ConfigFactory.load()
    val system = ActorSystem("ADStream", config)
    implicit val mat = ActorMaterializer()(system)
    implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(9092, 2181)
    val bootstrapServers = s"localhost:${embeddedKafkaConfig.kafkaPort}"

    def uuid = UUID.randomUUID().toString

    def createTopic(number: Int) = s"topic$number-" + uuid
    def createGroup(number: Int) = s"group$number-" + uuid

    val producerSettings =
        ProducerSettings(system, new ByteArraySerializer, new StringSerializer).withBootstrapServers(bootstrapServers)
    val topic1 = createTopic(1)
    val group1 = createGroup(1)

    EmbeddedKafka.start()
    val InitialMsg = "initial msg in topic, required to create the topic before any consumer subscribes to it"
    val producer = producerSettings.createKafkaProducer()
    producer.send(new ProducerRecord(topic1, 0, null: Array[Byte], InitialMsg))
    producer.close(60, TimeUnit.SECONDS)

    createProducer
    //createCommitConsumer
    val probe = createProbe(system, topic1)
    probe.request(100).expectNext()
    probe.cancel()

    Await.result(system.terminate(), 10.second)
    EmbeddedKafka.stop()


    def createProducer(): Unit = {
        val producer = producerSettings.createKafkaProducer()
        val file = Paths.get(getClass.getResource("/ad_proc_test.txt").getPath)

//        val result: Future[Done] = FileIO.fromPath(file)
//        .runForeach(n => println(n))
//        Await.result(result, 10.second)

//        FileIO.fromPath(file)
//        .via(Framing.delimiter(ByteString(System.lineSeparator),maximumFrameLength = 1024, allowTruncation = false))
//        .map(_.utf8String)
//        .runForeach(println)

//        .runWith(Sink.onComplete {
//            case Success(_) => println("Task done")
//            case Failure(e) => println(s"Failure: ${e.getMessage}")
//         })


        val source = FileIO.fromPath(file).
                via(Framing.delimiter(ByteString(System.lineSeparator),maximumFrameLength = 1024, allowTruncation = false)) .
                map(_.utf8String).
                map(n => {
                    val record = new ProducerRecord(topic1, 0, null:Array[Byte], n)
                    Message(record, NotUsed)
                }).viaMat(Producer.flow(producerSettings))(Keep.right)

        val result: Future[Done] = source.runWith(Sink.ignore)
        Await.result(result, 10.second)
    }

    def createCommitConsumer(): Unit = {
        val setting = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
                .withBootstrapServers("localhost:9092")
                .withGroupId(group1)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .withWakeupTimeout(10.seconds)
                .withMaxWakeups(10)


        val source = Consumer.committableSource(setting, Subscriptions.topics(topic1)).
                filterNot(_.record.value == InitialMsg).
                mapAsync(1) { msg =>
                    println(msg.record.value)
                    msg.committableOffset.commitScaladsl()
        }

        val result: Future[Done] = source.runWith(Sink.ignore)
        Await.result(result, 10.second)
    }

    def createProbe(_system: ActorSystem, topic: String)
        : TestSubscriber.Probe[String] = {
        implicit val system = _system
        val setting = ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
                .withBootstrapServers("localhost:9092")
                .withGroupId(group1)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .withWakeupTimeout(10.seconds)
                .withMaxWakeups(10)

        val partition = 0
        val subscription = Subscriptions.assignmentWithOffset(
            new TopicPartition(topic, partition) -> 0L)


        Consumer.plainSource(setting, subscription)
                .filterNot(_.value == InitialMsg)
                .map { n =>
                    println(n.value)
                    n.value
                }
                .runWith(TestSink.probe)
    }
}
