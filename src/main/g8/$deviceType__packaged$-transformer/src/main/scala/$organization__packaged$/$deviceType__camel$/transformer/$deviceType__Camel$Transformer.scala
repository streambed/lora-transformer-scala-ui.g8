package $organization;format="package"$.$deviceType;format="camel"$.transformer

import java.time.Instant

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString
import $organization;format="package"$.$deviceType;format="camel"$.{ $deviceType;format="Camel"$Reading, $deviceType;format="Camel"$ReadingJsonProtocol }
import com.cisco.streambed.UuidOps
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.durablequeue.opentracing.Headers
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.identity.streams.{ Streams => IdentityStreams }
import com.cisco.streambed.lora.streams.{ Streams => LoRaStreams }
import io.opentracing.{ References, Span, Tracer }
import spray.json._

/**
  * Run the transformation process to convert from LoRaWAN packets
  * to their $deviceType$ domain object expressed in json and then
  * re-published. The packets received on this topic have already
  * been verified by an NS and so do not require MIC or counter
  * verification. Any MacPayload data that is not ConfirmedDataUp
  * or UnconfirmedDataUp can also be safely ignored as it should
  * not be received here.
  */
object $deviceType;format="Camel"$Transformer {

  /**
    * The durable queue topic where transformations are published to
    */
  val $deviceType;format="Camel"$DataUpMacPayloadTopic: DurableQueue.Topic =
    "$deviceType;format="norm"$-data-up-mac-payload"

  /**
    * Provides a source to perform the transformation.
    */
  def source(durableQueue: DurableQueue, getSecret: Principal.GetSecret, tracer: Tracer)(
      implicit mat: Materializer
  ): Source[Span, NotUsed] = {
    import mat.executionContext
    val transform = Flow[DurableQueue.Event]
      .named("$deviceType;format="norm"$-transformer")
      .log("$deviceType;format="norm"$-transformer", identity)
      .map { case DurableQueue.Received(_, data, _, headers, _) => data -> headers }
      .map { case (data, headers) => data -> Headers.spanContext(headers, tracer) }
      .map {
        case (received, spanContext) =>
          val span = {
            val scope =
              tracer
                .buildSpan("$deviceType;format="norm"$-transformation")
                .addReference(References.FOLLOWS_FROM, spanContext)
                .startActive(false)
            try {
              scope.span()
            } finally {
              scope.close()
            }
          }
          received -> span
      }
      .via(LoRaStreams.dataUpDecoder(getSecret))
      .map {
        case ((nwkAddr, payload), span) =>
          ($deviceType;format="Camel"$Reading(Instant.now(), nwkAddr, payload.toArray), span)
      }
      .map {
        case (reading, span) =>
          import $deviceType;format="Camel"$ReadingJsonProtocol._
          (reading.nwkAddr -> reading.toJson.compactPrint, span)
      }
      .map {
        case ((nwkAddr, decryptedData), span) =>
          ((getSecret($deviceType;format="Camel"$Reading.$deviceType;format="Camel"$Key), ByteString(decryptedData)),
           (nwkAddr, span))
      }
      .via(IdentityStreams.encrypter)
      .map {
        case (encryptedData, (key, span)) =>
          DurableQueue.CommandRequest(
            DurableQueue.Send(key,
                              encryptedData,
                              $deviceType;format="Camel"$Reading.$deviceType;format="Camel"$DataUpJsonTopic,
                              Headers.headers(span.context(), tracer)),
            span
          )
      }
      .via(durableQueue.flow)
      .collect { case DurableQueue.CommandReply(DurableQueue.SendAck, Some(span)) => span }
      .wireTap(span => tracer.scopeManager().activate(span, true).close())
    durableQueue
      .resumableSource(
        $deviceType;format="Camel"$DataUpMacPayloadTopic,
        UuidOps.v5($deviceType;format="Camel"$MetaFilter.getClass),
        transform
      )
  }
}
