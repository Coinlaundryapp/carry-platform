package org.example.coin_laundry_app_backend.order.domain.enums.orderdetail;

public enum OrderDetailStatus {
    ORDER_COMPLETED,            // 주문 완료
    ORDER_CANCELED,             // 주문 취소
    PAYMENT_PENDING,            // 결제 대기
    PAYMENT_COMPLETED,          // 결제 완료
    DELIVERY_COMPLETED,         // 배송 완료
    REFUND_PENDING,             // 환불 대기 (승인 대기/승인 완료)
    REFUND_REQUEST_CANCELED,    // 환불 철회
    REFUND_COMPLETED;           // 환불 완료

    public boolean isPriceConfirmed() {
        return !this.equals(OrderDetailStatus.ORDER_COMPLETED) && !this.equals(OrderDetailStatus.ORDER_CANCELED);
    }
}
