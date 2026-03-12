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
    DISPATCH_NOT_PENDING(400, "Dispatch is not in pending status"),
    DISPATCH_ALREADY_ACCEPTED(409, "Dispatch already accepted"),
    CARRIER_NOT_IN_AREA(403, "Carrier is not registered in the dispatch area"),

    // Delivery
    DELIVERY_NOT_FOUND(404, "Delivery not found"),
    DELIVERY_INVALID_STATUS(400, "Delivery is not in expected status"),
    ORDER_NOT_PAID(402, "Order payment is not completed"),

    // Invoice
    INVOICE_NOT_FOUND(404, "Invoice not found"),
    INVOICE_ALREADY_PAID(409, "Invoice already paid"),
}
