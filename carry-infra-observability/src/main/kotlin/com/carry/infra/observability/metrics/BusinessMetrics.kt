package com.carry.infra.observability.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class BusinessMetrics(registry: MeterRegistry) {

    // Order
    private val orderCreated: Counter = Counter.builder("order.created.count")
        .description("Number of orders created")
        .register(registry)

    private val orderCancelled: Counter = Counter.builder("order.cancelled.count")
        .description("Number of orders cancelled")
        .register(registry)

    // Payment
    private val paymentCompleted: Counter = Counter.builder("payment.completed.count")
        .description("Number of payments completed")
        .register(registry)

    private val paymentFailed: Counter = Counter.builder("payment.failed.count")
        .description("Number of payments failed")
        .register(registry)

    // Dispatch
    private val dispatchAccepted: Counter = Counter.builder("dispatch.accepted.count")
        .description("Number of dispatches accepted")
        .register(registry)

    private val dispatchTimeout: Counter = Counter.builder("dispatch.timeout.count")
        .description("Number of dispatches timed out")
        .register(registry)

    fun incrementOrderCreated() = orderCreated.increment()
    fun incrementOrderCancelled() = orderCancelled.increment()
    fun incrementPaymentCompleted() = paymentCompleted.increment()
    fun incrementPaymentFailed() = paymentFailed.increment()
    fun incrementDispatchAccepted() = dispatchAccepted.increment()
    fun incrementDispatchTimeout() = dispatchTimeout.increment()
}
