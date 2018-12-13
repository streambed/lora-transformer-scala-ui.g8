package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.lora.controlplane.EndDeviceEvents
import com.cisco.streambed.lora.controlplane.EndDeviceEvents.LatLng
import com.cisco.streambed.testkit.durablequeue.InMemoryQueue
import com.cisco.streambed.testkit.storage.StorageOps
import java.time.Instant
import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import utest._

object EndDeviceServiceTest extends TestSuite {

  implicit lazy val system: ActorSystem =
    ActorSystem("end-device-service-tests")

  override def utestAfterAll(): Unit =
    system.terminate()

  implicit lazy val mat: Materializer =
    ActorMaterializer()

  implicit lazy val ec: ExecutionContext =
    mat.executionContext

  val tests = Tests {
    val getSecret: Principal.GetSecret = { path =>
      Future.successful(
        Right(
          Principal.SecretRetrieved(
            Principal.AuthorizedSecret("2B7E151628AED2A6ABF7158809CF4F3C",
                                       10.seconds)
          )
        )
      )
    }

    val durableQueue = InMemoryQueue.queue()

    val storage = StorageOps.storage()

    'works - {
      for {
        // Publish some end device events
        _ <- Source(
          Seq(
            EndDeviceEvents.PositionUpdated(1,
                                            Instant.ofEpochSecond(0),
                                            LatLng(-10, 10, None)),
            EndDeviceEvents.PositionUpdated(2,
                                            Instant.ofEpochSecond(0),
                                            LatLng(-10, 10, None)),
            EndDeviceEvents.PositionUpdated(1,
                                            Instant.ofEpochSecond(0),
                                            LatLng(-20, 20, None)),
            EndDeviceEvents.VersionUpdated(1, 1),
            EndDeviceEvents.NwkAddrRemoved(2)
          ))
          .via(EndDeviceEvents.appender(getSecret))
          .via(durableQueue.flow)
          .runWith(Sink.ignore)

        // Use service to retrieve events, saving a snapshot after processing
        // five items

        initialEvents <- EndDeviceService
          .events(durableQueue,
                  getSecret,
                  storage,
                  finite = true,
                  10000,
                  _ == 5)
          .runWith(Sink.collection)

        // Wait for our snapshot to be saved

        _ = eventually {
          Await
            .result(
              storage.load(LatestEndDeviceEvents,
                           EndDeviceService.latestEndDeviceEventsId),
              3.seconds
            )
            .isDefined
        }

        // Retrieve the readings again, now that snapshot has been saved

        snapshotEvents <- EndDeviceService
          .events(durableQueue,
                  getSecret,
                  storage,
                  finite = true,
                  10000,
                  _ => false)
          .runWith(Sink.collection)
      } yield {
        initialEvents ==> Seq(
          EndDeviceEvents.PositionUpdated(1,
                                          Instant.ofEpochSecond(0),
                                          LatLng(-10, 10, None)) -> 0,
          EndDeviceEvents.PositionUpdated(2,
                                          Instant.ofEpochSecond(0),
                                          LatLng(-10, 10, None)) -> 1,
          EndDeviceEvents.PositionUpdated(1,
                                          Instant.ofEpochSecond(0),
                                          LatLng(-20, 20, None)) -> 2,
          EndDeviceEvents.VersionUpdated(1, 1) -> 3,
          EndDeviceEvents.NwkAddrRemoved(2) -> 4
        )

        snapshotEvents ==> Seq(
          EndDeviceEvents.VersionUpdated(1, 1) -> 4
        )
      }
    }

  }
}
