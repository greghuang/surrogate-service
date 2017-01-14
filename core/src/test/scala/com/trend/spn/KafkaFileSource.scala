package com.trend.spn

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.testkit.scaladsl.TestSink
import akka.{Done, NotUsed}
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Keep, Sink}
import akka.stream.testkit.{StreamSpec, TestSubscriber}
import akka.testkit.AkkaSpec
import akka.util.ByteString
import com.typesafe.config.Config
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}
import org.scalatest._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Created by GregHuang on 1/13/17.
 */
abstract class KafkaFileSource(props: Config) extends StreamSpec with BeforeAndAfterEach {
    implicit val mat = ActorMaterializer()(system)
    implicit val ec = system.dispatcher
    implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(9092, 2181)

    val consumerConf = props.getConfig("akka.kafka.consumer")
    val topic = consumerConf.getString("topic")
    val partition = 0
    val groupID = "testgroup_0"
    val InitialMsg = "initial msg in topic, required to create the topic before any consumer subscribes to it"
    val bootstrapServers = consumerConf.getString("kafka-clients.bootstrap.servers")
    val sourcePath = consumerConf.getString("sourcePath")

    val producerSettings =
        ProducerSettings(system, new ByteArraySerializer, new StringSerializer).withBootstrapServers(bootstrapServers)

    override protected def beforeEach(): Unit = {
        EmbeddedKafka.start()
        createFileProducer(sourcePath)
    }

    override def afterEach(): Unit = {
        EmbeddedKafka.stop()
    }

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
