package com.chae.promo.outbox.repository;

import com.chae.promo.outbox.entity.EventOutbox;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long>  {
    @Query(value = """
        SELECT * FROM event_outbox
        WHERE status IN ('PENDING','FAILED')
          AND next_retry_at <= :now
        ORDER BY next_retry_at, id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<EventOutbox> lockAndFetch(@Param("now") LocalDateTime now,
                                   @Param("limit") int limit);
}
