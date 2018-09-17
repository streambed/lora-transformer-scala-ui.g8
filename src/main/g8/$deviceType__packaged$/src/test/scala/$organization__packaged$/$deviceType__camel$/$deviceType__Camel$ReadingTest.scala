package $organization;format="package"$.$deviceType;format="camel"$

import java.time.Instant

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.cisco.streambed.HexString
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.identity.streams.Streams
import spray.json._
import utest._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

object $deviceType;format="Camel"$ReadingTest extends TestSuite {

  implicit lazy val system: ActorSystem =
    ActorSystem("$deviceType;format="norm"$-tests")

  override def utestAfterAll(): Unit =
    system.terminate()

  implicit lazy val mat: Materializer =
    ActorMaterializer()

  implicit lazy val ec: ExecutionContext =
    mat.executionContext

  val tests = Tests {
    'parseBytes - {
      val instant = Instant.now
      val nwkAddr = 1
        $deviceType;format="Camel"$Reading(instant, nwkAddr, HexString.hexToBytes("025800a181f0")) ==> $deviceType;format="Camel"$Reading(
        instant,
        nwkAddr,
        BigDecimal(200, 1),
        BigDecimal(161, 1)
      )
    }

    'encodeDecodeJson - {
      import $deviceType;format="Camel"$ReadingJsonProtocol._
      val reading = $deviceType;format="Camel"$Reading(
        Instant.EPOCH,
        1,
        BigDecimal(200, 1),
        BigDecimal(161, 1)
      )
      val json = reading.toJson
      json ==> """{"time":"1970-01-01T00:00:00Z","nwkAddr":1,"temperature":20.0,"moisturePercentage":16.1}""".parseJson
      json.convertTo[$deviceType;format="Camel"$Reading] ==> reading
    }

    'tail - {
      val encryptionKey = "2B7E151628AED2A6ABF7158809CF4F3C"

      val getSecret: Principal.GetSecret = { _ =>
        Future.successful(
          Right(Principal.SecretRetrieved(Principal.AuthorizedSecret(encryptionKey, 1.minute)))
        )
      }

      val nwkAddr  = 1
      val temp     = 20.0
      val moisture = 12.2

      val _ =
        Source
          .single(
            s"""{"time":"1970-01-01T00:00:00Z","nwkAddr":1,"temperature":\$temp,"moisturePercentage":\$moisture}"""
          )
          .map { data =>
            ((getSecret($deviceType;format="Camel"$Reading.$deviceType;format="Camel"$Key), ByteString(data)), NotUsed)
          }
          .via(Streams.encrypter)
          .map {
            case (encryptedData, _) =>
              DurableQueue.Received(nwkAddr,
                                    encryptedData,
                                    0,
                                    DurableQueue.EmptyHeaders,
                                    $deviceType;format="Camel"$Reading.$deviceType;format="Camel"$DataUpJsonTopic)
          }
          .via($deviceType;format="Camel"$Reading.tailer(getSecret))
          .runWith(Sink.head)
          .map(_ ==> $deviceType;format="Camel"$Reading(Instant.EPOCH, nwkAddr, temp, moisture))
    }
  }
}
