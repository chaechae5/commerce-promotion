### 이벤트 시작
```
RedisKeyExpirationListener → Redis → Outbox -> Kafka → Consumer: SSE
```
1. RedisKeyExpirationListener가 키 만료 이벤트 수신
2. 분산 락 획득
    1. 이벤트 상태 : open ->  멱등 처리
    2. 이벤트 상태 : pending
        - 이벤트 상태 open으로 변경
        - outbox 저장 : `EVENT_OPEN`
5. 스케줄 키 삭제
6. 락 해제


```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as EventScheduler
    participant Redis as Redis(ZSET)
    participant Listener as KeyExpirationListener
    participant Lock as RedisLock
    participant DB as OutboxDB
    participant Kafka as Kafka
    participant Consumer as SSEConsumer
    participant Client as Client(Browser)

    Scheduler->>Redis: 이벤트 상태=pending 저장
    Scheduler->>Redis: ZSET 스케줄 키 등록

    Note over Listener: (시간 경과 후)
    Redis-->>Listener: Key Expired Event 수신

    Listener->>Lock: 분산 락 획득 시도
    alt 락 획득 실패
        Listener-->>Listener: 종료 (다른 노드에서 이미 처리 중)
    else 락 획득 성공
        Listener->>Redis: 이벤트 상태 조회
        alt 상태=open
            Listener-->>Listener: 멱등 처리 후 종료
            Lock-->>Listener: unlock
        else 상태=pending
            Listener->>Redis: 상태 open으로 변경
            Listener->>DB: Outbox 저장 (EVENT_OPEN)
            Listener->>Redis: ZSET 스케줄 키 삭제
            Lock-->>Listener: unlock
        end
    end

    DB->>Kafka: EVENT_OPEN 발행 (Outbox Worker)
    Kafka->>Consumer: Consume EVENT_OPEN
    Consumer-->>Client: SSE 이벤트 시작 알림(push)

```
