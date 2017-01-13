package com.trend.spn

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by GregHuang on 1/13/17.
 */
class IntegrationSpec(props: Config) extends KafkaFileSource(props) {
    def this() = this(
        ConfigFactory.parseString( """
          akka.kafka.consumer{
            kafka-clients.bootstrap.servers = "localhost:9092"
            topic = test01
            sourcePath = /ad_proc_100_test.txt
          }
                                   """)
          .withFallback(ConfigFactory.load())
    )


}
