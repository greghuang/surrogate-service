package com.trend.spn

import com.typesafe.config.Config

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

class SurrogateIntegrationSpec(config: Config) extends IntegrationSpec(config) {
  def this() = this(IntegrationSpec.config("inmem", "AccountSpec", "on", Some(SurrogateIntegrationSpec.testConf)))

  "An account" must {
    "receive the event and update the last host" in {

    }
  }
}
