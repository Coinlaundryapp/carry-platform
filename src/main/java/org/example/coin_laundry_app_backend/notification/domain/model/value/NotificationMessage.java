package org.example.coin_laundry_app_backend.notification.domain.model.value;

import lombok.Getter;

// TODO: 전화번호 인증 구현을 위한 임시 구현
@Getter
public class NotificationMessage {

    private final String msgCd;
    private final String to;
    private final String[] params;

    public NotificationMessage(String msgCd, String to, String[] params) {
        this.msgCd = msgCd;
        this.to = to;
        this.params = params;
    }
}
