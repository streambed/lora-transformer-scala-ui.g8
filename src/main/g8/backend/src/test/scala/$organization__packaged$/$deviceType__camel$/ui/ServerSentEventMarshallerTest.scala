package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.http.scaladsl.model.sse.ServerSentEvent
import com.cisco.streambed.lora.controlplane.EndDeviceEvents._
import java.time.Instant
import utest._

object ServerSentEventMarshallerTest extends TestSuite {
  val tests = Tests {
    '$deviceType;format="camel"$Reading - {
      val now = Instant.ofEpochSecond(0)

      ServerSentEventMarshaller($deviceType;format="Camel"$Reading(now, 1, BigDecimal("5.5"), BigDecimal("1.5")),
                                123) ==>
        ServerSentEvent(
          """{"time":"1970-01-01T00:00:00Z","nwkAddr":1,"temperature":5.5,"moisturePercentage":1.5}""",
          "$deviceType;format="Camel"$Reading",
          "123")
    }

    'endDeviceEvents - {
      val now = Instant.ofEpochSecond(0)

      ServerSentEventMarshaller(
        PositionUpdated(1, now, LatLng(BigDecimal(12), BigDecimal(34), None)),
        123) ==> ServerSentEvent(
        """{"nwkAddr":1,"time":"1970-01-01T00:00:00Z","position":{"lat":12,"lng":34},"type":"PositionUpdated"}""",
        "PositionUpdated",
        "123"
      )
    }
  }
}
