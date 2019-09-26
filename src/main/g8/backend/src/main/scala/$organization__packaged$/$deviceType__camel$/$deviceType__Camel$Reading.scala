package $organization;format="package"$.$deviceType;format="camel"$

import java.lang
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletionStage
import java.util.function

import akka.NotUsed
import akka.stream.javadsl.{ Flow => JFlow }
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.identity.{Crypto, Principal}
import spray.json._

import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

object $deviceType;format="Camel"$ReadingJsonProtocol extends $deviceType;format="Camel"$ReadingJsonProtocol

/**
  * A JSON codec for [[$deviceType;format="Camel"$Reading]]
  */
trait $deviceType;format="Camel"$ReadingJsonProtocol extends DefaultJsonProtocol {
  implicit val instantFormat: JsonFormat[Instant] =
    new JsonFormat[Instant] {
      override def write(obj: Instant): JsValue =
        JsString(obj.toString)
      override def read(json: JsValue): Instant =
        Instant.parse(json.convertTo[String])
    }

  // FIXME: You will need to replace the "4" with the actual number of parameters for your constructor if they differ
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
  def apply(time: Instant, nwkAddr: Int, payload: Array[Byte]): Option[$deviceType;format="Camel"$Reading] = {
    val TempOffset = BigDecimal(400, 1)

    if (payload.length >= 4) {
      val iter = ByteBuffer.wrap(payload)
      val t    = BigDecimal(iter.getShort & 0xFFFF, 1) - TempOffset
      val m    = BigDecimal(iter.getShort & 0xFFFF, 1)
      Some(new $deviceType;format="Camel"$Reading(time, nwkAddr, t, m))
    } else {
      None
    }
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
      .mapAsync(1) {
        case DurableQueue.Received(_, encryptedData, o, _, _) =>
          Crypto
            .decrypt(getSecret($deviceType;format="Camel"$Key), encryptedData)
            .filter(!_.left.exists(_ == Principal.Unauthenticated))
            .map(_ -> o)
      }
      .collect {
        case (Right(data), o) =>
          try {
            import $deviceType;format="Camel"$ReadingJsonProtocol._
            Some((data.utf8String.parseJson.convertTo[$deviceType;format="Camel"$Reading], o))
          } catch {
            case _: DeserializationException | _: JsonParser.ParsingException |
                _: NumberFormatException =>
              None
          }
      }
      .collect {
        case Some(e) => e
      }

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
  
  /**
    * A convenience function for encoding `$deviceType;format="Camel"$Reading` instances, encrypting them and then
    * publishing to a queue.
    */
  def appender[A](getSecret: Principal.GetSecret)(
      implicit ec: ExecutionContext
  ): Flow[($deviceType;format="Camel"$Reading, A), DurableQueue.CommandRequest[A], NotUsed] =
    Flow[($deviceType;format="Camel"$Reading, A)]
      .map {
        case (reading, carry) =>
          import $deviceType;format="Camel"$ReadingJsonProtocol._

          (reading.nwkAddr, reading.toJson.compactPrint, carry)
      }
      .mapAsync(1) {
        case (nwkAddr, decryptedData, carry) =>
          Crypto
            .encrypt(getSecret($deviceType;format="Camel"$Key), ByteString(decryptedData))
            .collect {
              case Right(bytes) =>
                DurableQueue.CommandRequest(
                  DurableQueue.Send(nwkAddr, bytes, $deviceType;format="Camel"$DataUpJsonTopic),
                  carry
                )
            }
      }

  /**
    * A convenience function for encoding `$deviceType;format="Camel"$Reading` instances, encrypting them and then
    * publishing to a queue.
    */
  def appender[A](
      getSecret: function.Function[
        String,
        CompletionStage[
          Either[Principal.FailureResponse, Principal.SecretRetrieved]
        ]],
      ec: ExecutionContext)
    : JFlow[($deviceType;format="Camel"$Reading, A), DurableQueue.CommandRequest[A], NotUsed] =
    Flow[($deviceType;format="Camel"$Reading, A)]
      .via(appender(getSecret.asScala.andThen(toScala))(ec))
      .asJava
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
