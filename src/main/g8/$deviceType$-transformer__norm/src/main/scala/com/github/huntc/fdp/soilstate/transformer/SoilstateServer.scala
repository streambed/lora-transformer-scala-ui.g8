/*
 * Copyright (c) Cisco Inc, 2018
 */

package com.github.huntc.fdp.soilstate.transformer

import akka.stream.scaladsl.Sink
import com.github.huntc.streambed.{ Application, ApplicationContext, ApplicationProcess }
// #imports
import com.github.huntc.streambed.durablequeue.chroniclequeue.DurableQueueProvider
import com.github.huntc.streambed.identity.iox.SecretStoreProvider
// #imports
import com.github.huntc.streambed.tracing.jaeger.TracerConfig

// #main
// #entry
/**
  * Bootstraps our application and handles signals
  */
object SoilstateServerEntryPoints {
  private val applicationProcess = ApplicationProcess(SoilstateServer)

  def main(args: Array[String]): Unit =
    applicationProcess.main(args)

  def trap(signal: Int): Unit =
    applicationProcess.trap(signal)
}
// #entry

// #di
/**
  * The Soil moisture/temp application
  */
object SoilstateServer extends Application with DurableQueueProvider with SecretStoreProvider {
  // #di

  // #mainmethod
  /**
    * Main entry point for the transformer and filter.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def main(args: Array[String], context: ApplicationContext): Unit = {

    import context._

    val tracer          = TracerConfig.tracer(config)
    val instrumentation = new SoilstateInstrumentation(tracer)

    {
      val _ = SoilstateMetaFilter
        .source(durableQueue, getSecret, instrumentation, tracer)
        .runWith(Sink.ignore)
    }
    {
      val _ = SoilstateTransformer
        .source(durableQueue, getSecret, instrumentation, tracer)
        .runWith(Sink.ignore)
    }
  }
  // #mainmethod
}
// #main
