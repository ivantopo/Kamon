package kamon.instrumentation.spring

import org.springframework.boot.actuate.autoconfigure.metrics.`export`.properties.PushRegistryProperties
import org.springframework.boot.context.properties.ConfigurationProperties

import scala.beans.BeanProperty

@ConfigurationProperties(prefix = "management.metrics.export.kamon-apm")
class KamonApmProperties extends PushRegistryProperties {

  /**
    * The Kamon APM API Key for the environment where you want to send your data
    */
  @BeanProperty var apiKey: String = _

  /**
    * The Kamon APM API Key for the environment where you want to send your data
    */
  @BeanProperty var uri: String = "https://ingestion.apm.kamon.io/v1"

}
