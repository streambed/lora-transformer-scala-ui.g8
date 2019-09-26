package $organization;format="package"$.$deviceType;format="camel"$.transformer

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import $organization;format="package"$.$deviceType;format="camel"$.$deviceType;format="Camel"$Reading
import com.cisco.streambed.HexString.{ hexToBytes, hexToInt }
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.testkit.durablequeue.InMemoryQueue
import io.opentracing.noop.NoopTracerFactory

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import utest._

object $deviceType;format="Camel"$TransformerTest extends TestSuite {

  implicit lazy val system: ActorSystem =
    ActorSystem("$deviceType;format="norm"$-transformer-tests")

  override def utestAfterAll(): Unit =
    system.terminate()

  implicit lazy val mat: Materializer =
    ActorMaterializer()

  implicit lazy val ec: ExecutionContext =
    mat.executionContext

  val tests = Tests {
    'transform - {
      val durableQueue  = InMemoryQueue.queue("some-namespace")
      val encryptionKey = "2B7E151628AED2A6ABF7158809CF4F3C" // Used for encrypting/decrypting everything
      val getSecret: Principal.GetSecret = { _ =>
        Future.successful(
          Right[Principal.FailureResponse, Principal.SecretRetrieved](
            Principal.SecretRetrieved(
              Principal.AuthorizedSecret(encryptionKey, 1.second)
            )
          )
        )
      }
      val tracer = NoopTracerFactory.create()

      // Kick off the transformer
      $deviceType;format="Camel"$Transformer
        .source(durableQueue, getSecret, tracer)
        .runWith(Sink.ignore)

      /*
       * Enqueue a LoRaWAN payload as a Network Server would. Uses the packet encoder utility to obtain
       * these values i.e.:
       *
       * docker run --rm farmco/lora-packet-encoder:0.9.0 \
       *   2B7E151628AED2A6ABF7158809CF4F3C \
       *   49be7df1 \
       *   2b11ff0d
       *
       * The first param is the AppSKey as hex, the second is the DevAddr as hex and the third
       * is the payload as hex.
       * FIXME: Change the AppSKey, DevAddr and payload to suit your device
       */
      val nwkAddr = hexToInt("01be7df1")
      val payload = hexToBytes("40f17dbe49000200017e84fa392b11ff0d")

      Source
        .single(
          DurableQueue.CommandRequest(
            DurableQueue.Send(nwkAddr,
                              ByteString(payload),
                              $deviceType;format="Camel"$Transformer.$deviceType;format="Camel"$DataUpMacPayloadTopic)
          )
        )
        .via(durableQueue.flow)
        .runWith(Sink.head)

      // Pull out the transformed domain object - we use the $deviceType$ reading tailer as a convenience to decrypt etc.
      assertMatch(
        Await.result(durableQueue
                       .source($deviceType;format="Camel"$Reading.$deviceType;format="Camel"$DataUpJsonTopic)
                       .via($deviceType;format="Camel"$Reading.tailer(getSecret))
                       .runWith(Sink.head),
                     3.seconds)
      ) {
        case ($deviceType;format="Camel"$Reading(time, devAddr, temp, moisture), _)
            // FIXME: Change to assert what should be matched
            if time.isBefore(Instant.now()) && devAddr == nwkAddr && temp == 20.0 && moisture == 16.1 =>
      }
    }
  }
}
