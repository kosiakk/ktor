package org.jetbrains.ktor.metrics

import com.codahale.metrics.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

class Metrics(val registry: MetricRegistry) {
    private val duration = registry.timer(MetricRegistry.name(ApplicationCall::class.java, "duration"))
    private val active = registry.counter(MetricRegistry.name(ApplicationCall::class.java, "active"))
    private val exceptions = registry.meter(MetricRegistry.name(ApplicationCall::class.java, "exceptions"))

    class Configuration {
        val registry = MetricRegistry()
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Metrics> {
        override val key = AttributeKey<Metrics>("metrics")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Metrics {
            val configuration = Configuration().apply(configure)
            val feature = Metrics(configuration.registry)
            val phase = PipelinePhase("Metrics")
            pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, phase)
            pipeline.intercept(phase) {
                feature.before(call)
                try {
                    proceed()
                } catch (e: Exception) {
                    feature.exception(call, e)
                    throw e
                } finally {
                    feature.after(call)
                }
            }

            return feature
        }
    }


    private data class CallMeasure(val timer: Timer.Context)

    private val measureKey = AttributeKey<CallMeasure>("metrics")

    private fun before(call: ApplicationCall) {
        active.inc()
        call.attributes.put(measureKey, CallMeasure(duration.time()))
    }

    private fun after(call: ApplicationCall) {
        active.dec()
        call.attributes.getOrNull(measureKey)?.apply {
            timer.stop()
        }
    }

    private fun exception(call: ApplicationCall, e: Throwable) {
        exceptions.mark()
    }
}