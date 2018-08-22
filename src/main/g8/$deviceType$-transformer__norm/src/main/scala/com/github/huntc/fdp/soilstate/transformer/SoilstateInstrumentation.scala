/*
 * Copyright (c) Cisco Inc, 2018
 */

package com.github.huntc.fdp.soilstate
package transformer

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.opentracing._

/**
  * Provides instrumentation for the transformer
  */
final class SoilstateInstrumentation(tracer: Tracer) {

  // #begin
  def beginTransformationEvent[A]: Flow[(A, SpanContext), (A, Span), NotUsed] =
    Flow.fromFunction[(A, SpanContext), (A, Span)] {
      case (received, spanContext) =>
        val span = {
          val scope =
            tracer
              .buildSpan("soilstate-transformation")
              .addReference(References.FOLLOWS_FROM, spanContext)
              .startActive(false)
          try {
            scope.span()
          } finally {
            scope.close()
          }
        }
        received -> span
    }
  // #begin

  // #end
  def endTransformationEvent: Flow[Span, Span, NotUsed] =
    Flow.fromFunction[Span, Span] { span =>
      tracer.scopeManager().activate(span, true).close()
      span
    }
  // #end

  def beginEventFilteringEvent[A]: Flow[(A, SpanContext), (A, Span), NotUsed] =
    Flow.fromFunction[(A, SpanContext), (A, Span)] {
      case (received, spanContext) =>
        val span = {
          val scope =
            tracer
              .buildSpan("soilstate-event-filtering")
              .addReference(References.FOLLOWS_FROM, spanContext)
              .startActive(false)
          try {
            scope.span()
          } finally {
            scope.close()
          }
        }
        received -> span
    }

  def endEventFilteringEvent: Flow[Span, Span, NotUsed] =
    Flow.fromFunction[Span, Span] { span =>
      tracer.scopeManager().activate(span, true).close()
      span
    }
}
