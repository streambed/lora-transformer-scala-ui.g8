package $organization;format="package"$.$deviceType;format="camel"$

import java.lang
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletionStage
import java.util.function

import akka.NotUsed
import akka.stream.javadsl.{ Flow => JFlow }
import akka.stream.scaladsl.Flow
import akka.stream.{ ActorAttributes, Supervision }
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.identity.streams.Streams
import spray.json._

import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

/**
  * A JSON codec for [[$deviceType;format="Camel"$Reading]]
  */
object $deviceType;format="Camel"$ReadingJsonProtocol extends DefaultJsonProtocol {
  implicit val instantFormat: JsonFormat[Instant] =
    new JsonFormat[Instant] {
      override def write(obj: Instant): JsValue =
        JsString(obj.toString)
      override def read(json: JsValue): Instant =
        Instant.parse(json.convertTo[String])
    }

  // FIXME: You will need to replace the "4" with the actual number of parameters for your constructor
  implicit val $deviceType;format="camel"$ReadingFormat: JsonFormat[$deviceType;format="Camel"$Reading] =
    jsonFormat4($deviceType;format="Camel"$Reading.apply)
}

object $deviceType;format="Camel"$Reading {

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
  def apply(time: Instant, nwkAddr: Int, payload: Array[Byte]): $deviceType;format="Camel"$Reading = {
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
    new $deviceType;format="Camel"$Reading(time, nwkAddr, temperature, moisturePercentage)
  }

  val $deviceType;format="Camel"$DataUpJsonTopic: DurableQueue.Topic =
    "$deviceType;format="norm"$-data-up-json"

  val $deviceType;format="Camel"$Key: String =
    "secrets.$deviceType;format="norm"$.key"

  /**
    * Conveniently tail, decrypt and decode readings. Yields the reading and its offset.
    */
  def tailer(getSecret: Principal.GetSecret)(
      implicit ec: ExecutionContext
  ): Flow[DurableQueue.Received, ($deviceType;format="Camel"$Reading, Long), NotUsed] =
    Flow[DurableQueue.Received]
      .map {
        case DurableQueue.Received(_, encryptedData, o, _, _) =>
          ((getSecret($deviceType;format="Camel"$Key), encryptedData), o)
      }
      .via(Streams.decrypter)
      .map {
        case (data, o) =>
          import $deviceType;format="Camel"$ReadingJsonProtocol._
          (data.utf8String.parseJson.convertTo[$deviceType;format="Camel"$Reading], o)
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
  ): JFlow[DurableQueue.Received, ($deviceType;format="Camel"$Reading, lang.Long), NotUsed] =
    tailer(getSecret.asScala.andThen(toScala))(ec).map {
      case (reading, o) => (reading, long2Long(o))
    }.asJava
}

/**
  * The domain object being received from LoRaWAN.
  *
  * FIXME: Change the declaration to represent your domain object.
  */
final case class $deviceType;format="Camel"$Reading(time: Instant,
                                  nwkAddr: Int,
                                  temperature: BigDecimal,
                                  moisturePercentage: BigDecimal)
