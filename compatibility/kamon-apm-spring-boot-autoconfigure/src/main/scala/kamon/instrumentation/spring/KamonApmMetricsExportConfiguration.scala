package kamon.instrumentation.spring

import io.micrometer.core.instrument.Clock
import kamon.compatibility.micrometer.{KamonConfig, KamonMeterRegistry}
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.autoconfigure.metrics.`export`.ConditionalOnEnabledMetricsExport
import org.springframework.boot.actuate.autoconfigure.metrics.{CompositeMeterRegistryAutoConfiguration, MetricsAutoConfiguration}
import org.springframework.boot.actuate.autoconfigure.metrics.`export`.simple.SimpleMetricsExportAutoConfiguration
import org.springframework.boot.autoconfigure.{AutoConfigureAfter, AutoConfigureBefore}
import org.springframework.boot.autoconfigure.condition.{ConditionalOnBean, ConditionalOnClass, ConditionalOnMissingBean}
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.{Bean, Configuration}

@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(Array(classOf[CompositeMeterRegistryAutoConfiguration], classOf[SimpleMetricsExportAutoConfiguration]))
@AutoConfigureAfter(Array(classOf[MetricsAutoConfiguration]))
@ConditionalOnBean(Array(classOf[Clock]))
@ConditionalOnClass(Array(classOf[KamonMeterRegistry]))
@ConditionalOnEnabledMetricsExport("kamon-apm")
@EnableConfigurationProperties(Array(classOf[KamonApmProperties]))
class KamonApmMetricsExportConfiguration(val properties: KamonApmProperties, @Value("${spring.application.name}") val appName: String) {

  @Bean
  @ConditionalOnMissingBean
  def kamonApmConfig(): KamonConfig = {
    new KamonApmPropertiesConfigAdapter(properties, appName)
  }

  @Bean
  @ConditionalOnMissingBean
  def kamonApmMeterRegistry(kamonConfig: KamonConfig, clock: Clock): KamonMeterRegistry = {
    new KamonMeterRegistry(kamonConfig, clock)
  }
}
