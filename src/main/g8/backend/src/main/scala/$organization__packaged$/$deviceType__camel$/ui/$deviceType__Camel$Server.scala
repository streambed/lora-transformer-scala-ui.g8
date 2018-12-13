package $organization;format="package"$.$deviceType;format="camel"$.ui

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import $organization;format="package"$.$deviceType;format="camel"$.transformer._
import com.cisco.streambed.durablequeue.remote.DurableQueueProvider
import com.cisco.streambed.http.HttpServerConfig
import com.cisco.streambed.http.identity.UserIdentityService
import com.cisco.streambed.identity.iox.SecretStoreProvider
import com.cisco.streambed.storage.fs.RawStorageProvider
import com.cisco.streambed.tracing.jaeger.TracerConfig
import com.cisco.streambed.{Application, ApplicationContext, ApplicationProcess}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Bootstraps our application and handles signals
 */
object $deviceType;format="Camel"$ServerEntryPoints {
  private val applicationProcess = ApplicationProcess($deviceType;format="Camel"$Server)

  def main(args: Array[String]): Unit =
    applicationProcess.main(args)

  def trap(signal: Int): Unit =
    applicationProcess.trap(signal)
}

/**
 * This is our main entry point to the application being responsible for serving assets as well as providings the UI's
 * RESTful endpoints
 */

object $deviceType;format="Camel"$Server
    extends Application
    with DurableQueueProvider
    with RawStorageProvider
    with SecretStoreProvider {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def main(args: Array[String], context: ApplicationContext): Unit = {

    implicit val applicationContext: ApplicationContext = context
    implicit val executionContext: ExecutionContext = context.system.dispatcher
    implicit val mat: Materializer = context.mat
    implicit val system: ActorSystem = context.system

    val tracer = TracerConfig.tracer(context.config)
    val userIdentityService = UserIdentityService(context)(context.system)

    val maxSensors = context.config.getInt(
      "$deviceType;format="norm"$.maximum-nr-of-sensors")
    val saveEvery = context.config.getLong(
      "$deviceType;format="norm"$.save-interval")

    {
      val _ = $deviceType;format="Camel"$MetaFilter
        .source(context.durableQueue, context.getSecret, tracer)
        .runWith(Sink.ignore)
        .onComplete {
          case Success(Done) => System.exit(0)
          case Failure(_)    => System.exit(1)
        }
    }

    {
      val _ = $deviceType;format="Camel"$Transformer
        .source(context.durableQueue, context.getSecret, tracer)
        .runWith(Sink.ignore)
        .onComplete {
          case Success(Done) => System.exit(0)
          case Failure(_)    => System.exit(2)
        }
    }

    {
      $deviceType;format="Camel"$Service
        .latestReadings(context.durableQueue,
                        context.getSecret,
                        context.storage,
                        finite = false,
                        maxSensors,
                        _ % saveEvery == 0)
        .runWith(Sink.ignore)
        .onComplete {
          case Success(_) =>
            System.exit(0)

          case Failure(e) =>
            context.system.log
              .error(e, "$deviceType;format="Camel"$Service#latestReadings failed, exiting")
            System.exit(1)
        }
    }

    {
      EndDeviceService
        .events(context.durableQueue,
                context.getSecret,
                context.storage,
                finite = false,
                maxSensors,
                _ % saveEvery == 0)
        .runWith(Sink.ignore)
        .onComplete {
          case Success(_) =>
            System.exit(0)

          case Failure(e) =>
            context.system.log
              .error(e, "EndDeviceService#events failed, exiting")
            System.exit(1)
        }
    }

    {
      val _ = HttpServerConfig
        .bindAndHandle($deviceType;format="Camel"$Routes(maxSensors, userIdentityService))
        .onComplete {
          case Success(bs) =>
            bs.foreach { b =>
              context.system.log.info("Server listening on {}", b.localAddress)
            }

          case Failure(e) =>
            context.system.log.error(e, "Bind failed, exiting")
            System.exit(1)
        }
    }
  }
}
