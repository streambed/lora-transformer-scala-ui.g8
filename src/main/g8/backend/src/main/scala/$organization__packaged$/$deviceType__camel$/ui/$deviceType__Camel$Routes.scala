package $organization;format="package"$.$deviceType;format="camel"$.ui

import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import com.cisco.streambed.http.identity.UserIdentityService
import com.cisco.streambed.identity.model.UserCredentials
import com.cisco.streambed.ApplicationContext
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import spray.json._

object $deviceType;format="Camel"$Routes
    extends Directives
    with DefaultJsonProtocol
    with EventStreamMarshalling
    with SprayJsonSupport {
  private val paramFinite = parameter("finite".as[Boolean].?(false))

  implicit val userCredentialsFormat: RootJsonFormat[UserCredentials] =
    jsonFormat2(UserCredentials)

  /*_*/
  def apply(maxSensors: Int, userIdentityService: UserIdentityService)(
      implicit context: ApplicationContext,
      executionContext: ExecutionContext,
      mat: Materializer
  ): Route = concat(
    (post & path("api" / "login"))(
      Route.seal(
        entity(as[UserCredentials]) {
          case UserCredentials(username, password) =>
            onSuccess(userIdentityService.authenticate(username, password)) {
              case Some(token) => complete(token)
              case None =>
                complete(StatusCodes.Unauthorized -> "Bad credentials")
            }
        }
      )
    ),
    pathPrefix("api")(
      Route.seal(
        authenticateOAuth2Async("secured api", userIdentityService.verifier) {
          principal =>
            concat(
              (get & path("end-devices") & paramFinite)(
                finite =>
                  complete(
                    EndDeviceService
                      .events(context.durableQueue,
                              principal.getSecret,
                              context.storage,
                              finite,
                              maxSensors,
                              _ => false)
                      .map {
                        case (event, offset) =>
                          ServerSentEventMarshaller(event, offset)
                      }
                      .keepAlive(10.seconds, () => ServerSentEvent.heartbeat)
                )),
              (get & path("$deviceType;format="norm"$") & paramFinite)(
                finite =>
                  complete(
                    $deviceType;format="Camel"$Service
                      .latestReadings(context.durableQueue,
                                      principal.getSecret,
                                      context.storage,
                                      finite,
                                      maxSensors,
                                      _ => false)
                      .map {
                        case (event, offset) =>
                          ServerSentEventMarshaller(event, offset)
                      }
                      .keepAlive(10.seconds, () => ServerSentEvent.heartbeat)
                ))
            )
        }
      )
    ),
    pathEndOrSingleSlash(getFromResource("dist/index.html")),
    getFromResourceDirectory("dist")
  )
  /*_*/
}
