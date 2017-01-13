package com.trend.spn

import akka.stream.ActorMaterializer
import akka.stream.testkit.StreamSpec
import com.typesafe.config.ConfigFactory
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest.BeforeAndAfterEach

/**
 * Created by GregHuang on 1/13/17.
 */
class IntegrationSpec extends StreamSpec with BeforeAndAfterEach {
    val props =
        ConfigFactory.parseString( """
        akka.kafka.consumer.kafka-clients.bootstrap.servers = "localhost:9092"
        akka.kafka.consumer.topic = test01""")
        .withFallback(ConfigFactory.load())

    implicit val config = props.getConfig("akka.kafka.consumer")
    implicit val mat = ActorMaterializer()(system)
    implicit val ec = system.dispatcher
    implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(9092, 2181)
}
