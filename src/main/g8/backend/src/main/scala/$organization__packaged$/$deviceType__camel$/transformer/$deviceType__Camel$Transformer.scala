package $organization;format="package"$.$deviceType;format="camel"$
package transformer

import java.time.Instant

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.cisco.streambed.UuidOps
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.durablequeue.opentracing.Headers
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.lora.streams.{ Streams => LoRaStreams }
import io.opentracing.{ References, Span, Tracer }

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
    val topic = $deviceType;format="Camel"$DataUpMacPayloadTopic
    val id = UuidOps.v5($deviceType;format="Camel"$Transformer.getClass)
    Source
      .fromFuture(durableQueue.offset(topic, id))
      .flatMapConcat { offset =>
        durableQueue
          .source(topic, offset, finite = false)
          .dropWhile(r => offset.contains(r.offset))
      }
      .named("$deviceType;format="norm"$-transformer")
      .log("$deviceType;format="norm"$-transformer", identity)
      .map { received =>
        received -> Headers.spanContext(received.headers, tracer)
      }
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
          received.data -> (received, span)
      }
      .via(LoRaStreams.dataUpDecoder(getSecret))
      .map {
        case ((nwkAddr, _, payload), carry) =>
          ($deviceType;format="Camel"$Reading(Instant.now(), nwkAddr, payload.toArray),
           carry)
      }
      .collect { case (Some(reading), carry) => (reading, carry) }
      .via($deviceType;format="Camel"$Reading.appender(getSecret))
      .via(durableQueue.flow)
      .collect { case DurableQueue.CommandReply(DurableQueue.SendAck, Some(carry)) => carry }
      .via(durableQueue.commit(id))
      .wireTap(span => tracer.scopeManager().activate(span, true).close())
  }
}
