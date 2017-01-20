package com.trend.spn

import akka.kafka.ConsumerMessage.CommittableMessage
import akka.stream.{KillSwitches, ClosedShape}
import akka.util.Timeout
import akka.{NotUsed, Done}
import akka.kafka.scaladsl.Consumer
import com.trend.spn.router.Message

import scala.language.postfixOps
import scala.concurrent.duration._

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.{AutoSubscription, Subscriptions, ConsumerSettings, ProducerSettings}
import akka.stream.scaladsl._
import com.typesafe.config.{ConfigFactory, Config}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, ByteArrayDeserializer, StringSerializer, ByteArraySerializer}

import scala.concurrent.Future

/**
 * Created by GregHuang on 1/9/17.
 */
object ADReactiveStream {
    def apply(_system: ActorSystem, props: Config): ADReactiveStream = new ADReactiveStream(_system, props)
}

class ADReactiveStream(system: ActorSystem, props: Config) {
    val config = props.getConfig("akka.kafka.consumer")
    //val accountConfig = props.getConfig("holmes.matrix.account")
    val bootstrapServers = config.getString("kafka-clients.bootstrap.servers")

    implicit val executor = system.dispatcher

    def getKafkaConsumer(topic: String, partition: Int): Source[String, Consumer.Control] = {
        val setting = ConsumerSettings[Array[Byte], String](system, new ByteArrayDeserializer, new StringDeserializer)
                .withBootstrapServers(bootstrapServers)
                .withGroupId("0")
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .withWakeupTimeout(10 seconds)
                .withMaxWakeups(10)

        val subscription = Subscriptions.topics(topic)

        val msgToString: Flow[ConsumerRecord[Array[Byte], String], String, NotUsed] = Flow[ConsumerRecord[Array[Byte], String]].map(_.value)

        val committableMsgToString: Flow[CommittableMessage[Array[Byte], String], String, NotUsed] =
            Flow[CommittableMessage[Array[Byte], String]]
                    .mapAsync(1) { msg =>
                msg.committableOffset.commitScaladsl().map(_ => msg.record.value())
            }

        //Consumer.plainSource(setting, subscription).viaMat(msgToString)(Keep.right)
        Consumer.committableSource(setting, subscription)
                .viaMat(committableMsgToString)(Keep.left)
    }

//    def flowAccountMatrix(accountMatrix: ActorRef)(implicit timeout: Timeout): Flow[String, Int, NotUsed] = {
//        Flow[String].mapAsync(5)(elem => (accountMatrix ? elem).mapTo[Int])
//    }

    def flowAccountMatrix(accountMatrix: ActorRef)(implicit timeout: Timeout): Flow[(Int, String), Int, NotUsed] = {
        Flow[(Int, String)].mapAsync(5)(elem => (accountMatrix ? Message(elem._1.toString, elem._2)).mapTo[Int])
    }

    def accountMatrixGraph(accountActor: ActorRef, topic: String, partition: Int)(implicit timeout: Timeout) = {
        accountMatrixGraph[Future[Done]](accountActor, topic, partition, Sink.ignore)
    }

    def accountMatrixGraph[T](accountActor: ActorRef, topic: String, partition: Int, sink: Sink[Int, T])(implicit timeout: Timeout) = {
        val source = getKafkaConsumer(topic, partition)
        val flow = flowAccountMatrix(accountActor)
        val kill = KillSwitches.single[Int]
//        val sink = Sink.ignore

        RunnableGraph.fromGraph(GraphDSL.create(source, flow, kill, sink)((_, _, _, _)) { implicit builder => (source, flow, kill, sink) =>
            import GraphDSL.Implicits._
            //Add an integer(1 ~ 3) as temporary key
            def aKey = Source.fromIterator(() => Iterator.from(1))
            val zip = builder.add(Zip[Int, String]())

            aKey.map(_ % 10) ~> zip.in0
            source ~> zip.in1
            zip.out ~> flow ~> kill ~> sink
            //source ~> flow ~> kill ~> sink
            ClosedShape
        })
    }

    def getSimpleStream(topic: String, partition: Int): RunnableGraph[Future[Done]] = {
        val kafkaStreamGraph: RunnableGraph[Future[Done]] =
            getKafkaConsumer(topic, partition)
                    .toMat(Sink.ignore)(Keep.right)

        kafkaStreamGraph
    }

    def getStreamWithActor(topic: String, partition: Int, actorRef: ActorRef)(implicit timeout: Timeout): Source[Int, NotUsed] = {
        val askToActor: Flow[String, Int, NotUsed] = Flow[String].mapAsync(5)(elem => (actorRef ? elem).mapTo[Int])

        val kafkaStreamGraph: Source[Int, NotUsed] =
            getKafkaConsumer(topic, partition).viaMat(askToActor)(Keep.right)

        kafkaStreamGraph
    }
}
