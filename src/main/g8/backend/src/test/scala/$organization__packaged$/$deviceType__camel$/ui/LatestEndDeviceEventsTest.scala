package $organization;format="package"$.$deviceType;format="camel"$.ui

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKitBase
import akka.util.ByteString
import com.cisco.streambed.lora.controlplane.EndDeviceEvents.{
  LatLng,
  NwkAddrUpdated,
  PositionUpdated
}
import com.cisco.streambed.storage.StateCodec
import java.time.Instant

import com.cisco.streambed.lora.packet.DevEUI

import scala.concurrent.{ExecutionContext, Future}
import utest._

object LatestEndDeviceEventsTest extends TestSuite with TestKitBase {
  override implicit lazy val system: ActorSystem =
    ActorSystem("latest-end-device-events-test")

  override def utestAfterAll(): Unit =
    system.terminate()

  private implicit lazy val mat: Materializer =
    ActorMaterializer()

  import system.dispatcher

  val tests = Tests {
    'encodeDecode - {
      'one - testStateCodec(
        LatestEndDeviceEvents,
        LatestEndDeviceEvents(None, List.empty)
      )

      'two - testStateCodec(
        LatestEndDeviceEvents,
        LatestEndDeviceEvents(
          Some(45),
          List(
            NwkAddrUpdated(1, DevEUI(1)),
            PositionUpdated(1, Instant.ofEpochSecond(0), LatLng(-10, 10, None))
          )
        )
      )
    }
  }

  def testStateCodec[T](codec: StateCodec[T], value: T)(
      implicit ec: ExecutionContext,
      mat: Materializer): Future[Unit] =
    for {
      encodedSource <- codec.encode(value)
      encodedData <- encodedSource.runFold(ByteString.empty)(_ ++ _)
      decoded <- codec.decode(Source.single(encodedData))
    } yield {
      decoded ==> value
    }
}
