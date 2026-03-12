package com.carry.common.exception

enum class ErrorCode(
    val status: Int,
    val message: String
) {
    // Common
    INVALID_INPUT(400, "Invalid input"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Resource not found"),
    INTERNAL_ERROR(500, "Internal server error"),

    // Order
    ORDER_NOT_FOUND(404, "Order not found"),
    INVALID_ORDER_STATUS_TRANSITION(400, "Invalid order status transition"),

    // Payment
    PAYMENT_NOT_FOUND(404, "Payment not found"),
    PAYMENT_ALREADY_COMPLETED(409, "Payment already completed"),

    // Dispatch
    DISPATCH_NOT_FOUND(404, "Dispatch not found"),
    NO_AVAILABLE_RIDER(503, "No available rider"),
}
