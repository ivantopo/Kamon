package kamon.instrumentation.spring

import kamon.compatibility.micrometer.KamonConfig
import org.springframework.boot.actuate.autoconfigure.metrics.`export`.properties.PushRegistryPropertiesConfigAdapter

class KamonApmPropertiesConfigAdapter(props: KamonApmProperties, val appName: String)
    extends PushRegistryPropertiesConfigAdapter[KamonApmProperties](props) with KamonConfig {

  override def prefix(): String =
    "management.metrics.export.kamon-apm"

  override def apiKey(): String =
    props.getApiKey()

  override def uri(): String =
    props.getUri()

}
