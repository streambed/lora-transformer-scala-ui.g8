package $organization;format="package"$.$deviceType;format="camel"$
package transformer

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Source }
import akka.stream.{ ActorAttributes, Materializer, Supervision }
import com.cisco.streambed.UuidOps
import com.cisco.streambed.HexString
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.durablequeue.opentracing.Headers
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.identity.streams.Streams
import com.cisco.streambed.lora.controlplane.EndDeviceEvents
import io.opentracing.{ References, Span, Tracer }
import spray.json._

/**
  * Run the filtering process to filter through end-device-events
  * meta data for $deviceType$ events only - the events get published to a
  * new topic and encrypted under the $deviceType$ key.
  */
object $deviceType;format="Camel"$MetaFilter {

  /**
    * The topic to publish end device events pertaining to $deviceType$
    */
  val $deviceType;format="Camel"$EventsTopic: DurableQueue.Topic =
    "$deviceType;format="norm"$-events"

  /**
    * Provides a source to perform the meta data filtering.
    */
  def source(
      durableQueue: DurableQueue,
      getSecret: Principal.GetSecret,
      tracer: Tracer
  )(implicit mat: Materializer): Source[Span, NotUsed] = {
    import mat.executionContext
    val metaFilter = Flow[DurableQueue.Event]
      .named("$deviceType;format="norm"$-meta")
      .log("$deviceType;format="norm"$-meta", identity)
      .map {
        case DurableQueue.Received(key, data, _, headers, _) => ((key, data), headers)
      }
      .map { case (data, headers) => (data, Headers.spanContext(headers, tracer)) }
      .map {
        case (received, spanContext) =>
          val span = {
            val scope =
              tracer
                .buildSpan("$deviceType;format="norm"$-event-filtering")
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
          // We only to publish certain events - other data could also be quite sensitive e.g. secrets, counters etc.
          import DefaultJsonProtocol._
          decryptedData.utf8String.toJson.asJsObject
            .getFields("type")
            .headOption match {
            case Some(eventType)
                if eventType == EndDeviceEvents.EventJsonProtocol.BatteryLevelUpdatedField ||
                  eventType == EndDeviceEvents.EventJsonProtocol.NameUpdatedField ||
                  eventType == EndDeviceEvents.EventJsonProtocol.NwkAddrRemovedField ||
                  eventType == EndDeviceEvents.EventJsonProtocol.NwkAddrUpdatedField ||
                  eventType == EndDeviceEvents.EventJsonProtocol.PositionUpdatedField =>
              true
            case _ => false
          }
      }
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .map {
        case (decryptedData, carry) =>
          ((getSecret($deviceType;format="Camel"$Reading.$deviceType;format="Camel"$Key), decryptedData), carry)
      }
      .via(Streams.encrypter)
      .map {
        case (encryptedData, (key, span)) =>
          DurableQueue.CommandRequest(DurableQueue.Send(key,
                                                        encryptedData,
                                                        $deviceType;format="Camel"$EventsTopic,
                                                        Headers.headers(span.context(),
                                                                        tracer)),
                                      span)
      }
      .via(durableQueue.flow)
      .collect { case DurableQueue.CommandReply(DurableQueue.SendAck, Some(span)) => span }
      .wireTap(span => tracer.scopeManager().activate(span, true).close())
    durableQueue
      .resumableSource(
        EndDeviceEvents.EventTopic,
        UuidOps.v5($deviceType;format="Camel"$MetaFilter.getClass),
        metaFilter
      )
  }
}
