package com.chae.promo.outbox.service;

public interface DomainOutboxPublisher {
    boolean supports(String eventType);
    void publish(String payloadJson) throws Exception;
}
