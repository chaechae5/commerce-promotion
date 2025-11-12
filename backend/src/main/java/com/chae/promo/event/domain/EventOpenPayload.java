package com.chae.promo.event.domain;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOpenPayload {
    private String outboxEventId; // outbox 이벤트 ID
    private String eventDomainId; // 이벤트 도메인 ID
    private String status; // 상태

}
