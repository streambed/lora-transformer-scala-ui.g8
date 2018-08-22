/*
 * Copyright (c) Cisco Inc, 2018
 */

package com.github.huntc.fdp.soilstate.transformer

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Source }
import akka.stream.{ ActorAttributes, Materializer, Supervision }
import com.github.huntc.fdp.soilstate.SoilStateReading
import com.github.huntc.lora.controlplane.EndDeviceEvents
import com.github.huntc.streambed.UuidOps
import com.github.huntc.streambed.HexString
import com.github.huntc.streambed.durablequeue.DurableQueue
import com.github.huntc.streambed.durablequeue.opentracing.Headers
import com.github.huntc.streambed.identity.Principal
import com.github.huntc.streambed.identity.streams.Streams
import io.opentracing.{ Span, Tracer }
import spray.json._

/**
  * Run the filtering process to filter through end-device-events
  * meta data for soilstate events only - the events get published to a
  * new topic and encrypted under the soilstate key.
  */
object SoilstateMetaFilter {

  /**
    * The topic to publish end device events pertaining soil state to
    */
  val SoilStateEventsTopic: DurableQueue.Topic =
    "soilstate-events"

  /**
    * Provides a source to perform the meta data filtering.
    */
  def source(
      durableQueue: DurableQueue,
      getSecret: Principal.GetSecret,
      instrumentation: SoilstateInstrumentation,
      tracer: Tracer
  )(implicit mat: Materializer): Source[Span, NotUsed] = {
    import mat.executionContext
    durableQueue
      .resumableSource(
        EndDeviceEvents.EventTopic,
        UuidOps.v5(SoilstateMetaFilter.getClass),
        Flow[DurableQueue.Event]
          .named("soilstate-meta")
          .map {
            case DurableQueue.Received(key, data, _, headers, _) => ((key, data), headers)
          }
          .map { case (data, headers) => (data, Headers.spanContext(headers, tracer)) }
          .via(instrumentation.beginEventFilteringEvent)
          .map {
            case ((nwkAddr, data), span) =>
              val path = EndDeviceEvents.EventKey + "." + HexString.intToHex(nwkAddr.toInt)
              ((getSecret(path), data), (nwkAddr, span))
          }
          .via(Streams.decrypter)
          .filter {
            case (decryptedData, _) =>
              decryptedData.headOption
                .contains('{') // If we cannot decrypt then the sensor doesn't belong to us
          }
          .filter {
            case (decryptedData, _) =>
              // We only to publish position changes - other data could also be quite sensitive e.g. secrets, counters etc.
              import DefaultJsonProtocol._
              decryptedData.utf8String.toJson.asJsObject
                .getFields("type") == EndDeviceEvents.EventJsonProtocol.PositionUpdatedFields
          }
          .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
          .map {
            case (decryptedData, carry) =>
              ((getSecret(SoilStateReading.SoilStateKey), decryptedData), carry)
          }
          .via(Streams.encrypter)
          .map {
            case (encryptedData, (key, span)) =>
              DurableQueue.CommandRequest(DurableQueue.Send(key,
                                                            encryptedData,
                                                            SoilStateEventsTopic,
                                                            Headers.headers(span.context(),
                                                                            tracer)),
                                          span)
          }
          .via(durableQueue.flow)
          .collect { case DurableQueue.CommandReply(DurableQueue.SendAck, Some(span)) => span }
          .via(instrumentation.endEventFilteringEvent)
      )
  }
}
