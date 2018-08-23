package $organization;format="package"$.$deviceType;format="camel"$.transformer

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.opentracing._

/**
  * Provides instrumentation for the transformer
  */
final class $deviceType;format="Camel"$Instrumentation(tracer: Tracer) {

  def beginTransformationEvent[A]: Flow[(A, SpanContext), (A, Span), NotUsed] =
    Flow.fromFunction[(A, SpanContext), (A, Span)] {
      case (received, spanContext) =>
        val span = {
          val scope =
            tracer
              .buildSpan("$deviceType;format="norm"$-transformation")
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

  def endTransformationEvent: Flow[Span, Span, NotUsed] =
    Flow.fromFunction[Span, Span] { span =>
      tracer.scopeManager().activate(span, true).close()
      span
    }

  def beginEventFilteringEvent[A]: Flow[(A, SpanContext), (A, Span), NotUsed] =
    Flow.fromFunction[(A, SpanContext), (A, Span)] {
      case (received, spanContext) =>
        val span = {
          val scope =
            tracer
              .buildSpan("$deviceType;format="norm"$-event-filtering")
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
