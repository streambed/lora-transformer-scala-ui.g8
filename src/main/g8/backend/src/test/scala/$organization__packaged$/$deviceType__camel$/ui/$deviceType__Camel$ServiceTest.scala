package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.testkit.durablequeue.InMemoryQueue
import com.cisco.streambed.testkit.storage._
import java.time.Instant
import scala.collection.immutable.Seq
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import utest._

object $deviceType;format="Camel"$ServiceTest extends TestSuite {

  implicit lazy val system: ActorSystem =
    ActorSystem("$deviceType;format="norm"$-service-tests")

  override def utestAfterAll(): Unit =
    system.terminate()

  implicit lazy val mat: Materializer =
    ActorMaterializer()

  implicit lazy val ec: ExecutionContext =
    mat.executionContext

  val tests = Tests {
    val getSecret: Principal.GetSecret = { _ =>
      Future.successful(
        Right(
          Principal.SecretRetrieved(
            Principal.AuthorizedSecret("2B7E151628AED2A6ABF7158809CF4F3C",
                                       10.seconds)
          )
        )
      )
    }

    val durableQueue = InMemoryQueue.queue("some-namespace")

    val storage = StorageOps.storage()

    'works - {
      for {
        // Publish some readings
        _ <- Source(Seq(
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 1, BigDecimal("5.5"), BigDecimal("5.5")),
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 2, BigDecimal("7.5"), BigDecimal("7.5")),
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 1, BigDecimal("2.5"), BigDecimal("2.5"))
        )).map(_ -> NotUsed)
          .via($deviceType;format="Camel"$Reading.appender(getSecret))
          .via(durableQueue.flow)
          .runWith(Sink.ignore)

        // Use service to retrieve readings, saving a snapshot after processing
        // three items

        initialReadings <- $deviceType;format="Camel"$Service
          .latestReadings(durableQueue,
                          getSecret,
                          storage,
                          finite = true,
                          10000,
                          _ == 3)
          .runWith(Sink.collection)

        // Wait for our snapshot to be saved

        _ = eventually {
          Await
            .result(
              storage.load(LatestReadings,
                           $deviceType;format="Camel"$Service.latestReadingsId),
              3.seconds
            )
            .isDefined
        }

        // Retrieve the readings again, now that snapshot has been saved

        snapshotReadings <- $deviceType;format="Camel"$Service
          .latestReadings(durableQueue,
                          getSecret,
                          storage,
                          finite = true,
                          10000,
                          _ => false)
          .runWith(Sink.collection)
      } yield {
        initialReadings ==> Seq(
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 1, BigDecimal("5.5"), BigDecimal("5.5")) -> 0,
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 2, BigDecimal("7.5"), BigDecimal("7.5")) -> 1,
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 1, BigDecimal("2.5"), BigDecimal("2.5")) -> 2
        )

        snapshotReadings ==> Seq(
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 2, BigDecimal("7.5"), BigDecimal("7.5")) -> 2,
          $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0L), 1, BigDecimal("2.5"), BigDecimal("2.5")) -> 2
        )
      }
    }

  }
}
