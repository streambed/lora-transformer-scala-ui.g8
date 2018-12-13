package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.http.scaladsl.model.sse.ServerSentEvent
import com.cisco.streambed.lora.controlplane.EndDeviceEvents
import spray.json._

/**
  * Uses Spray JSON format instances to marshal data into
  * `ServerSentEvent` instances.
  */
object ServerSentEventMarshaller {
  import $deviceType;format="Camel"$ReadingJsonProtocol.$deviceType;format="camel"$ReadingFormat
  import EndDeviceEvents.EventJsonProtocol.eventFormat

  def apply(event: EndDeviceEvents.Event, offset: Long): ServerSentEvent = {
    val json = event.toJson
    json.asJsObject.fields
      .get("type")
      .flatMap {
        case JsString(value) => Some(value)
        case _ => None
      }
      .fold(ServerSentEvent.heartbeat)(t =>
        ServerSentEvent(json.asJsObject.compactPrint, t, offset.toString))
  }

  def apply(event: $deviceType;format="Camel"$Reading, offset: Long): ServerSentEvent =
    ServerSentEvent(event.toJson.asJsObject.compactPrint,
                    "$deviceType;format="Camel"$Reading",
                    offset.toString)
}
