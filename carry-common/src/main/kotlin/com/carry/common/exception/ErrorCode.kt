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
    CONFLICT(409, "Resource conflict"),
    CONCURRENT_MODIFICATION(409, "Concurrent modification detected — retry the request"),
    INTERNAL_ERROR(500, "Internal server error"),

    // User
    USER_NOT_FOUND(404, "User not found"),
    USER_ALREADY_DEACTIVATED(409, "User is already deactivated"),
    SHIPPING_ADDRESS_NOT_FOUND(404, "Shipping address not found"),
    SHIPPING_ADDRESS_LIMIT_EXCEEDED(400, "Shipping address limit exceeded"),

    // Laundromat
    LAUNDROMAT_NOT_FOUND(404, "Laundromat not found"),
    LAUNDROMAT_ALREADY_EXISTS(409, "Laundromat already exists"),

    // Price
    PRICE_POLICY_NOT_FOUND(404, "Price policy not found"),
    PRICE_POLICY_ALREADY_EXISTS(409, "Price policy already exists for the given condition"),
    OPTION_NOT_FOUND(404, "Option not found in price policy"),
    OPTION_NOT_SELECTABLE(400, "Option is not selectable"),

    // Geo
    GEOCODING_FAILED(502, "Geocoding request failed"),
    REVERSE_GEOCODING_FAILED(502, "Reverse geocoding request failed"),
    ADDRESS_NOT_FOUND(404, "Address not found"),
    GEOCODING_UNAVAILABLE(503, "Geocoding service is temporarily unavailable — retry later"),

    // Service Availability
    SERVICE_AREA_NOT_FOUND(404, "Service area not found"),
    AREA_NOT_ACTIVE(422, "Service is not active in the requested area"),
    OUTSIDE_OPERATING_HOURS(422, "Request is outside operating hours"),
    HOLIDAY_CLOSED(422, "Service is closed due to holiday"),

    // Order
    ORDER_NOT_FOUND(404, "Order not found"),
    INVALID_ORDER_STATUS_TRANSITION(400, "Invalid order status transition"),
    ORDER_NOT_CANCELLABLE(409, "Order cannot be cancelled in current status"),
    ORDER_NOT_OWNED(403, "Order does not belong to the user"),

    // Payment
    PAYMENT_NOT_FOUND(404, "Payment not found"),
    PAYMENT_ALREADY_COMPLETED(409, "Payment already completed"),
    PAYMENT_NOT_REFUNDABLE(409, "Payment is not in a refundable state"),
    PAYMENT_FAILED(502, "Payment processing failed"),
    PG_GATEWAY_UNAVAILABLE(503, "Payment gateway is temporarily unavailable — retry later"),
    UNSUPPORTED_PG_PROVIDER(400, "Unsupported payment gateway provider"),
    ORDER_NOT_PAID(402, "Order payment is not completed"),
    PAYMENT_NOT_OWNED(403, "Payment does not belong to the user"),

    // Invoice
    INVOICE_NOT_FOUND(404, "Invoice not found"),
    INVOICE_ALREADY_PAID(409, "Invoice already paid"),
    INVOICE_NOT_OWNED(403, "Invoice does not belong to the user"),

    // Dispatch
    DISPATCH_NOT_FOUND(404, "Dispatch not found"),
    DISPATCH_NOT_PENDING(400, "Dispatch is not in pending status"),
    DISPATCH_ALREADY_ACCEPTED(409, "Dispatch already accepted"),
    DISPATCH_NOT_CANCELLABLE(409, "Dispatch cannot be cancelled in current status"),
    DISPATCH_TIMEOUT_NOT_ALLOWED(400, "Dispatch timeout is only allowed in pending status"),
    CARRIER_NOT_IN_AREA(403, "Carrier is not registered in the dispatch area"),
    CARRIER_AREA_NOT_FOUND(404, "Carrier area registration not found"),
    DISPATCH_NOT_OWNED(403, "Dispatch is not assigned to the carrier"),

    // Delivery
    DELIVERY_NOT_FOUND(404, "Delivery not found"),
    DELIVERY_INVALID_STATUS(400, "Delivery is not in expected status"),
    DELIVERY_WEIGHT_REQUIRED(400, "Pickup weight must be greater than 0"),
    DELIVERY_PHOTO_REQUIRED(400, "At least one photo is required"),

    // Media
    MEDIA_NOT_FOUND(404, "Media resource not found"),
    MEDIA_UPLOAD_FAILED(502, "Media upload failed"),
    INVALID_FILE_TYPE(400, "Invalid file type"),
    INVALID_MEDIA_STATUS_TRANSITION(400, "Invalid media status transition"),

    // Review
    REVIEW_NOT_FOUND(404, "Review not found"),
    REVIEW_NOT_OWNED(403, "Review does not belong to the user"),
    DUPLICATE_REVIEW(409, "Review already exists for this order"),

    // Notification
    NOTIFICATION_NOT_FOUND(404, "Notification not found"),
    INVALID_DEVICE_PLATFORM(400, "Unsupported device platform"),

    // Operation
    TERM_NOT_FOUND(404, "Term not found"),
    TERM_ALREADY_DEACTIVATED(409, "Term is already deactivated"),
}
