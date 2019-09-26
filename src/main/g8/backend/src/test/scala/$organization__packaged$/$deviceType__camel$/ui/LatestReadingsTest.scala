package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKitBase
import akka.util.ByteString
import com.cisco.streambed.storage.StateCodec
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import utest._
import spray.json._

object LatestReadingsTest extends TestSuite with TestKitBase {
  override implicit lazy val system: ActorSystem =
    ActorSystem("latest-readings-test")

  override def utestAfterAll(): Unit =
    system.terminate()

  private implicit lazy val mat: Materializer =
    ActorMaterializer()

  import system.dispatcher

  val tests = Tests {
    'encodeDecode - {
      'one - testStateCodec(
        LatestReadings,
        LatestReadings(None, List.empty),
        ByteString("""{"readings":[]}""")
      )

      'two - testStateCodec(
        LatestReadings,
        LatestReadings(
          Some(45),
          List(
            $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0), 1, BigDecimal("5.5"), BigDecimal("1.5")),
            $deviceType;format="Camel"$Reading(Instant.ofEpochSecond(0), 2, BigDecimal("8.5"), BigDecimal("2.5"))
          )
        ),
        ByteString(
          """{"offset":45,"readings":[{"time":"1970-01-01T00:00:00Z","nwkAddr":1,"temperature":5.5,"moisturePercentage":1.5},{"time":"1970-01-01T00:00:00Z","nwkAddr":2,"temperature":8.5,"moisturePercentage":2.5}]}""".parseJson.compactPrint)
      )
    }
  }

  def testStateCodec[T](codec: StateCodec[T], value: T, expected: ByteString)(
      implicit ec: ExecutionContext,
      mat: Materializer): Future[Unit] =
    for {
      encodedSource <- codec.encode(value)
      encodedData <- encodedSource.runFold(ByteString.empty)(_ ++ _)
      decoded <- codec.decode(Source.single(encodedData))
    } yield {
      encodedData ==> expected
      decoded ==> value
    }
}
