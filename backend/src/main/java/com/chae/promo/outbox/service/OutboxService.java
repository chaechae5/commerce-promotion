package com.chae.promo.outbox.service;

public interface OutboxService {

    void saveEvent(String eventId, String type, String aggregateId, Object payload);

    boolean existsByTypeAndAggregateId(String topic, String aggregateId);
}
