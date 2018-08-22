package $organization;format="package"$.$deviceType;format="package"$

import java.lang
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletionStage
import java.util.function

import akka.NotUsed
import akka.stream.javadsl.{ Flow => JFlow }
import akka.stream.scaladsl.Flow
import akka.stream.{ ActorAttributes, Supervision }
import com.github.huntc.streambed.durablequeue.DurableQueue
import com.github.huntc.streambed.identity.Principal
import com.github.huntc.streambed.identity.streams.Streams
import spray.json._

import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

/**
  * A JSON codec for [[$deviceType;format="upper-camel"$]]
  */
object $deviceType;format="upper-camel"$JsonProtocol extends DefaultJsonProtocol {
  implicit val instantFormat: JsonFormat[Instant] =
    new JsonFormat[Instant] {
      override def write(obj: Instant): JsValue =
        JsString(obj.toString)
      override def read(json: JsValue): Instant =
        Instant.parse(json.convertTo[String])
    }

  // FIXME: You will need to replace the "4" with the actual number of parameters for your constructor
  implicit val $deviceType;format="lower-camel"$ReadingFormat: JsonFormat[$deviceType;format="upper-camel"$] =
    jsonFormat4(SoilStateReading.apply)
}

object $deviceType;format="upper-camel"$Reading {

  /**
    * Construct from a decrypted LoRaWAN ConfirmedDataUp/UnconfirmedDataUp FRMPayload
    *
    * FIXME: The following example assumes a soil moisture/temperature sensors with 4 bytes.
    * The first 2 bytes are the temperature in Celsuis and 400 added to it (for fun).
    * The second 2 bytes represent the moisture as a percentage.
    *
    * You should change this function to decode your LoRaWAN payload from its byte
    * representation.
    */
  def apply(time: Instant, nwkAddr: Int, payload: Array[Byte]): SoilStateReading = {
    val TempOffset = BigDecimal(400, 1)
    val Zero       = BigDecimal(0, 1)

    val (temperature, moisturePercentage) = if (payload.length >= 4) {
      val iter = ByteBuffer.wrap(payload)
      val t    = BigDecimal(iter.getShort & 0xFFFF, 1) - TempOffset
      val m    = BigDecimal(iter.getShort & 0xFFFF, 1)
      t -> m
    } else {
      Zero -> Zero
    }
    new $deviceType;format="upper-camel"$Reading(time, nwkAddr, temperature, moisturePercentage)
  }

  val $deviceType;format="upper-camel"$DataUpJsonTopic: DurableQueue.Topic =
    "$deviceType;format="normalize"$-data-up-json"

  val $deviceType;format="upper-camel"$Key: String =
    "secrets.$deviceType;format="normalize"$.key"

  /**
    * Conveniently tail, decrypt and decode readings. Yields the reading and its offset.
    */
  def tailer(getSecret: Principal.GetSecret)(
      implicit ec: ExecutionContext
  ): Flow[DurableQueue.Received, (SoilStateReading, Long), NotUsed] =
    Flow[DurableQueue.Received]
      .map {
        case DurableQueue.Received(_, encryptedData, o, _, _) =>
          ((getSecret($deviceType;format="upper-camel"$Key), encryptedData), o)
      }
      .via(Streams.decrypter)
      .map {
        case (data, o) =>
          import $deviceType;format="upper-camel"$ReadingJsonProtocol._
          (data.utf8String.parseJson.convertTo[$deviceType;format="upper-camel"$Reading], o)
      }
      .withAttributes(
        ActorAttributes.supervisionStrategy(Supervision.resumingDecider)
      )

  /**
    * Conveniently tail, decrypt and decode readings. Yields the reading and its offset.
    */
  def tailer(
      getSecret: function.Function[String, CompletionStage[
        Either[Principal.FailureResponse, Principal.SecretRetrieved]
      ]],
      ec: ExecutionContext
  ): JFlow[DurableQueue.Received, ($deviceType;format="upper-camel"$Reading, lang.Long), NotUsed] =
    tailer(getSecret.asScala.andThen(toScala))(ec).map {
      case (reading, o) => (reading, long2Long(o))
    }.asJava
}

/**
  * The domain object being received from LoRaWAN.
  *
  * FIXME: Change the declaration to represent your domain object.
  */
final case class $deviceType;format="upper-camel"$Reading(time: Instant,
                                  nwkAddr: Int,
                                  temperature: BigDecimal,
                                  moisturePercentage: BigDecimal)
