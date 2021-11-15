package kamon.compatibility.micrometer

import io.micrometer.core.instrument.push.PushRegistryConfig

trait KamonConfig extends PushRegistryConfig {

  def uri(): String
  def apiKey(): String
  def appName(): String
}
