package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.cisco.streambed.storage.StateCodec
import scala.concurrent.{ExecutionContext, Future}
import spray.json._

/**
  * An event sourced object of all observations retaining just the last one
  */
case class LatestReadings(offset: Option[Long],
                          readings: List[$deviceType;format="Camel"$Reading])

object LatestReadings
  extends StateCodec[LatestReadings]
    with DefaultJsonProtocol
    with $deviceType;format="Camel"$ReadingJsonProtocol {
  private implicit val format: RootJsonFormat[LatestReadings] =
    jsonFormat2(LatestReadings.apply)

  override val key: String = "latest-readings"

  override def decode(source: Source[ByteString, NotUsed])(
    implicit ec: ExecutionContext,
    mat: Materializer): Future[LatestReadings] =
    source
      .runFold(ByteString.empty)(_ ++ _)
      .map(_.utf8String)
      .map(_.parseJson.convertTo[LatestReadings])

  override def encode(instance: LatestReadings)(
    implicit ec: ExecutionContext,
    mat: Materializer): Future[Source[ByteString, NotUsed]] =
    Future.successful(Source.single(ByteString(instance.toJson.compactPrint)))
}
