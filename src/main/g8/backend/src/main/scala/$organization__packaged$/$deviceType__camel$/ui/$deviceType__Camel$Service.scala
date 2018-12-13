package $organization;format="package"$.$deviceType;format="camel"$
package ui

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl._
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.storage.Storage
import com.cisco.streambed.UuidOps
import scala.concurrent.ExecutionContext

/**
  * Manages services in relation to `$deviceType;format="Camel"$Reading`
  */
object $deviceType;format="Camel"$Service {
  private[ui] val latestReadingsId =
    UuidOps.v5($deviceType;format="Camel"$Service.getClass, "latestReadings")

  /**
    * Creates a `Source` of `$deviceType;format="Camel"$Reading` that emits the latest reading for
    * every sensor followed by any additional readings as they occur.
    */
  def latestReadings(durableQueue: DurableQueue,
                     getSecret: Principal.GetSecret,
                     storage: Storage,
                     finite: Boolean,
                     maxSensors: Int,
                     saveSnapshot: Long => Boolean)(
      implicit mat: Materializer,
      ec: ExecutionContext): Source[($deviceType;format="Camel"$Reading, Long), NotUsed] =
    Source
      .fromFutureSource(
        storage
          .load(LatestReadings, latestReadingsId)
          .map { maybeSnapshot =>
            Source.single(
              maybeSnapshot.getOrElse(LatestReadings(None, List.empty)))
          }
      )
      .flatMapConcat { initialState =>
        Source(initialState.readings.reverse)
          .map(reading => (reading, initialState.offset.getOrElse(0L)))
          .concat(
            durableQueue
              .source($deviceType;format="Camel"$Reading.$deviceType;format="Camel"$DataUpJsonTopic,
                      initialState.offset,
                      finite)
              .dropWhile(r => initialState.offset.contains(r.offset))
              .via($deviceType;format="Camel"$Reading.tailer(getSecret))
              .scan((initialState, Option.empty[$deviceType;format="Camel"$Reading], 0L)) {
                case ((state, _, processed), (reading, offset)) =>
                  val latestReadings =
                    LatestReadings(Some(offset),
                                   (reading :: state.readings.filterNot(
                                     _.nwkAddr == reading.nwkAddr))
                                     .take(maxSensors))

                  if (saveSnapshot(processed + 1)) {
                    val _ = storage.save(LatestReadings,
                                         latestReadingsId,
                                         latestReadings)
                  }

                  (latestReadings, Some(reading), processed + 1)
              }
              .collect {
                case (LatestReadings(Some(offset), _), Some(reading), _) =>
                  reading -> offset
              }
          )
      }
      .mapMaterializedValue(_ => NotUsed)
}
