package com.trend.spn

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import akka.kafka.{Subscriptions, ConsumerSettings, ProducerSettings}
import akka.stream.testkit.scaladsl.TestSink
import akka.{Done, NotUsed}
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Keep, Framing, FileIO}
import akka.stream.testkit.{TestSubscriber, StreamSpec}
import akka.util.ByteString
import com.typesafe.config.Config
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, ByteArrayDeserializer, StringSerializer, ByteArraySerializer}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Created by GregHuang on 1/13/17.
 */
abstract class FileSourceKafkaSpec(_props: Config) extends StreamSpec {
    implicit val mat = ActorMaterializer()(system)
    implicit val ec = system.dispatcher
    implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(9092, 2181)

    val props = _props.getConfig("akka.kafka.consumer")
    val topic = props.getString("topic")
    val partition = 0
    val groupID = "testgroup_0"
    val InitialMsg = "initial msg in topic, required to create the topic before any consumer subscribes to it"
    val bootstrapServers = props.getString("kafka-clients.bootstrap.servers")
    val sourcePath = props.getString("sourcePath")

    val producerSettings =
        ProducerSettings(system, new ByteArraySerializer, new StringSerializer).withBootstrapServers(bootstrapServers)

    override def atStartup(): Unit = {
        EmbeddedKafka.start()
        createFileProducer(sourcePath)
    }

    override def beforeTermination(): Unit = {
        EmbeddedKafka.stop()
    }

    //override def afterTermination(): Unit = {}

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
}
