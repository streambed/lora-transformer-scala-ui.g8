/*
 * Copyright (c) Cisco Inc, 2018
 */

package com.github.huntc.fdp.soilstate
package transformer

import java.time.Instant

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString
import com.github.huntc.lora.streams.{ Streams => LoRaStreams }
import com.github.huntc.streambed.UuidOps
import com.github.huntc.streambed.durablequeue.DurableQueue
import com.github.huntc.streambed.durablequeue.opentracing.Headers
import com.github.huntc.streambed.identity.Principal
import com.github.huntc.streambed.identity.streams.{ Streams => IdentityStreams }
import io.opentracing.{ Span, Tracer }
import spray.json._

// #transformer
/**
  * Run the transformation process to convert from LoRaWAN packets
  * to their soilstate domain object expressed in json and then
  * re-published. The packets received on this topic have already
  * been verified by an NS and so do not require MIC or counter
  * verification. Any MacPayload data that is not ConfirmedDataUp
  * or UnconfirmedDataUp can also be safely ignored as it should
  * not be received here.
  */
object SoilstateTransformer {

  // #a
  /**
    * The durable queue topic where transformations are published to
    */
  val SoilStateDataUpMacPayloadTopic: DurableQueue.Topic =
    "soilstate-data-up-mac-payload"
  // #a

  // #b
  /**
    * Provides a source to perform the transformation.
    */
  def source(durableQueue: DurableQueue,
             getSecret: Principal.GetSecret,
             instrumentation: SoilstateInstrumentation,
             tracer: Tracer)(implicit mat: Materializer): Source[Span, NotUsed] = {
    // #b
    import mat.executionContext
    // #c
    durableQueue
      .resumableSource(
        // #c
        SoilStateDataUpMacPayloadTopic,
        UuidOps.v5(SoilstateMetaFilter.getClass),
        // #d
        Flow[DurableQueue.Event]
          .named("soilstate")
          .map { case DurableQueue.Received(_, data, _, headers, _) => data -> headers }
          .map { case (data, headers) => data -> Headers.spanContext(headers, tracer) }
          // #d
          // #e
          .via(instrumentation.beginTransformationEvent)
          // #e
          // #f
          .via(LoRaStreams.dataUpDecoder(getSecret))
          // #f
          // #g
          .map {
            case ((nwkAddr, payload), span) =>
              (SoilStateReading(Instant.now(), nwkAddr, payload.toArray), span)
          }
          // #g
          // #h
          .map {
            case (reading, span) =>
              import SoilStateReadingJsonProtocol._
              (reading.nwkAddr -> reading.toJson.compactPrint, span)
          }
          // #h
          // #i
          .map {
            case ((nwkAddr, decryptedData), span) =>
              ((getSecret(SoilStateReading.SoilStateKey), ByteString(decryptedData)),
               (nwkAddr, span))
          }
          .via(IdentityStreams.encrypter)
          // #i
          // #j
          .map {
            case (encryptedData, (key, span)) =>
              DurableQueue.CommandRequest(
                DurableQueue.Send(key,
                                  encryptedData,
                                  SoilStateReading.SoilStateDataUpJsonTopic,
                                  Headers.headers(span.context(), tracer)),
                span
              )
          }
          .via(durableQueue.flow)
          .collect { case DurableQueue.CommandReply(DurableQueue.SendAck, Some(span)) => span }
          // #j
          // #k
          .via(instrumentation.endTransformationEvent)
        // #k
      )
  }
}
// #transformer
