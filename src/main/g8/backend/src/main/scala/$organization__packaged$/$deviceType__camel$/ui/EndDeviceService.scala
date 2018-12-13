package $organization;format="package"$.$deviceType;format="camel"$.ui

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.cisco.streambed.durablequeue.DurableQueue
import com.cisco.streambed.UuidOps
import com.cisco.streambed.identity.Principal
import com.cisco.streambed.lora.controlplane.EndDeviceEvents
import com.cisco.streambed.storage.Storage

import scala.concurrent.ExecutionContext

/**
  * Manages services in relation to end devices.
  */
object EndDeviceService {
  private[ui] val latestEndDeviceEventsId =
    UuidOps.v5(EndDeviceService.getClass, "latestEndDeviceEvents")

  /**
    * Creates a `Source` of end device events that emits the one for
    * every sensor followed by any additional events as they occur.
    */
  def events(durableQueue: DurableQueue,
             getSecret: Principal.GetSecret,
             storage: Storage,
             finite: Boolean,
             maxSensors: Int,
             saveSnapshot: Long => Boolean)(
      implicit mat: Materializer,
      ec: ExecutionContext): Source[(EndDeviceEvents.Event, Long), NotUsed] =
    Source
      .fromFutureSource(
        storage
          .load(LatestEndDeviceEvents, latestEndDeviceEventsId)
          .map { maybeSnapshot =>
            Source.single(
              maybeSnapshot.getOrElse(LatestEndDeviceEvents(None, List.empty)))
          }
      )
      .flatMapConcat { initialState =>
        Source(initialState.events.reverse)
          .map(event => (event, initialState.offset.getOrElse(0L)))
          .concat(
            durableQueue
              .source(EndDeviceEvents.EventTopic, initialState.offset, finite)
              .dropWhile(r => initialState.offset.contains(r.offset))
              .via(EndDeviceEvents.tailer(getSecret))
              .scan((initialState, Option.empty[EndDeviceEvents.Event], 0L)) {
                case ((state, _, processed), (event, offset)) =>
                  val (latestEndDeviceEvents, emitEvent) =
                    event match {
                      case e: EndDeviceEvents.NwkAddrRemoved =>
                        LatestEndDeviceEvents(
                          Some(offset),
                          state.events.filterNot(_.nwkAddr == e.nwkAddr)
                        ) -> true
                      case e: EndDeviceEvents.Event =>
                        LatestEndDeviceEvents(
                          Some(offset),
                          (e :: state.events.filterNot(_.nwkAddr == e.nwkAddr))
                            .take(maxSensors)) -> true
                    }

                  if (saveSnapshot(processed + 1)) {
                    val _ = storage.save(LatestEndDeviceEvents,
                                         latestEndDeviceEventsId,
                                         latestEndDeviceEvents)
                  }

                  (latestEndDeviceEvents,
                   if (emitEvent) Some(event) else None,
                   processed + 1)
              }
              .collect {
                case (LatestEndDeviceEvents(Some(offset), _), Some(event), _) =>
                  event -> offset
              }
          )
      }
      .mapMaterializedValue(_ => NotUsed)
}
