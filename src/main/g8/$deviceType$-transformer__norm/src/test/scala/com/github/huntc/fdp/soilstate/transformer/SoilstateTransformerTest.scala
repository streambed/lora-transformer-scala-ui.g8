/*
 * Copyright (c) Cisco Inc, 2018
 */

package com.github.huntc.fdp.soilstate.transformer

import java.nio.ByteBuffer
import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.ByteString
import com.github.huntc.fdp.soilstate.SoilStateReading
import com.github.huntc.lora.packet._
import com.github.huntc.streambed.durablequeue.DurableQueue
import com.github.huntc.streambed.identity.Principal
import com.github.huntc.streambed.testkit.durablequeue.InMemoryQueue
import io.opentracing.noop.NoopTracerFactory

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import utest._

object SoilstateTransformerTest extends TestSuite {

  implicit lazy val system: ActorSystem =
    ActorSystem("iox-reporter-tests")

  override def utestAfterAll(): Unit =
    system.terminate()

  implicit lazy val mat: Materializer =
    ActorMaterializer()

  implicit lazy val ec: ExecutionContext =
    mat.executionContext

  val tests = Tests {
    'transform - {
      val durableQueue  = InMemoryQueue.queue()
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
      val tracer          = NoopTracerFactory.create()
      val instrumentation = new SoilstateInstrumentation(tracer)

      // Kick off the transformer
      SoilstateTransformer
        .source(durableQueue, getSecret, instrumentation, tracer)
        .runWith(Sink.ignore)

      // Form a LoRaWAN payload and enqueue it as a Network Server would
      import com.github.huntc.streambed.HexString._
      val mic = MIC(ByteBuffer.wrap(hexToBytes("2b11ff0d")).getInt)
      val fHDR = FHDR(
        DevAddr(ByteBuffer.wrap(hexToBytes("49be7df1")).getInt),
        FCtrl(ByteBuffer.wrap(hexToBytes("00")).get),
        FCnt(ByteBuffer.wrap(hexToBytes("0002")).getShort),
        FOpts(List.empty)
      )
      val fPort                 = FPort(ByteBuffer.wrap(hexToBytes("01")).get)
      val unencryptedFrmPayload = FRMPayload(ByteBuffer.wrap(hexToBytes("025800a1")).array().toList) // The actual bytes to be encoded into a domain object
      val frmPayload =
        FRMPayload(
          unencryptedFrmPayload,
          FRMPayload.encryptBlocksWithAppSKey(
            AppSKey(hexToBytes(encryptionKey)),
            FRMPayload.fCntUpBlocks(fHDR.devAddr,
                                    fHDR.fCnt,
                                    FRMPayload.k(unencryptedFrmPayload.underlying.length))
          )
        )
      val unconfirmedDataUp = UnconfirmedDataUp(fHDR, Some(fPort), Some(frmPayload))

      def passThruMic(mic: MIC)(msg: Array[Byte], msgSize: Int): MIC =
        mic // Pass through is fine given that the Network Server has already done a MIC check and so it won't be done again by the transformer

      val payload = PHYPayloadCodec.encode(unconfirmedDataUp, passThruMic(mic)(_, _))

      val (_, _, nwkAddr) = fHDR.devAddr.nwkTypeIDAndAddr

      Source
        .single(
          DurableQueue.CommandRequest(
            DurableQueue.Send(nwkAddr,
                              ByteString(payload),
                              SoilstateTransformer.SoilStateDataUpMacPayloadTopic)
          )
        )
        .via(durableQueue.flow)
        .runWith(Sink.head)

      // Pull out the transformed domain object - we use the soil state reading tailer as a convenience to decrypt etc.
      assertMatch(
        Await.result(durableQueue
                       .source(SoilStateReading.SoilStateDataUpJsonTopic)
                       .via(SoilStateReading.tailer(getSecret))
                       .runWith(Sink.head),
                     3.seconds)
      ) {
        case (SoilStateReading(time, devAddr, temp, moisture), _)
            if time.isBefore(Instant.now()) && devAddr == nwkAddr && temp == 20.0 && moisture == 16.1 =>
      }
    }
  }
}
