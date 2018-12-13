package $organization;format="package"$.$deviceType;format="camel"$.ui

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.cisco.streambed.storage.StateCodec
import com.cisco.streambed.lora.controlplane.EndDeviceEvents.Event
import scala.concurrent.{ExecutionContext, Future}
import spray.json._

/**
  * An event sourced object of all end device events retaining just the last one
  */
case class LatestEndDeviceEvents(offset: Option[Long], events: List[Event])

object LatestEndDeviceEvents
  extends StateCodec[LatestEndDeviceEvents]
    with DefaultJsonProtocol {
  import com.cisco.streambed.lora.controlplane.EndDeviceEvents.EventJsonProtocol.eventFormat

  private implicit val format: RootJsonFormat[LatestEndDeviceEvents] =
    jsonFormat2(LatestEndDeviceEvents.apply)

  override val key: String = "latest-end-device-events"

  override def decode(source: Source[ByteString, NotUsed])(
    implicit ec: ExecutionContext,
    mat: Materializer): Future[LatestEndDeviceEvents] =
    source
      .runFold(ByteString.empty)(_ ++ _)
      .map(_.utf8String)
      .map(_.parseJson.convertTo[LatestEndDeviceEvents])

  override def encode(instance: LatestEndDeviceEvents)(
    implicit ec: ExecutionContext,
    mat: Materializer): Future[Source[ByteString, NotUsed]] =
    Future.successful(Source.single(ByteString(instance.toJson.compactPrint)))
}
