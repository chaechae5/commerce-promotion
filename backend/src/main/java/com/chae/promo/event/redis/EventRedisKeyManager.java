package com.chae.promo.event.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventRedisKeyManager {

    private final String envPrefix; // 환경별 prefix

    public EventRedisKeyManager(@Value("${app.redis.prefix}") String prefix) {
        // prefix가 null, 빈문자, 콜론 누락일 경우 안전하게 보정
        if (prefix == null || prefix.isBlank()) {
            this.envPrefix = "";
        } else if (!prefix.endsWith(":")) {
            this.envPrefix = prefix + ":";
        } else {
            this.envPrefix = prefix;
        }
    }

    private static final String EVENT_PREFIX = "event"; //도메인 prefix

    private static final String EVENT_START_FLAG_KEY_FORMAT = "{%s}:start_flag"; // %s = eventId
    private static final String EVENT_STATUS_KEY_FORMAT = "{%s}:status"; // %s = eventId
    private static final String EVENT_SCHEDULE_KEY_SUFFIX = "schedule";
    private static final String EVENT_LOCK_KEY_FORMAT = "lock:{%s}"; // %s = eventId


    /** 이벤트 시작 플래그 키 생성
     * @param eventId 이벤트 ID
     * @return Redis 이벤트 시작 플래그 Key
     */
    public String getEventStartFlagKey(String eventId) {
        validateEventId(eventId);
        return buildKey(String.format(EVENT_START_FLAG_KEY_FORMAT, eventId));
    }

    /** 이벤트 상태 키 생성
     * @param eventId 이벤트 ID
     * @return Redis 이벤트 상태 Key
     */
    public String getEventStatusKey(String eventId) {
        validateEventId(eventId);
        return buildKey(String.format(EVENT_STATUS_KEY_FORMAT, eventId));
    }

    /** 이벤트 스케줄 키 생성
     * @return Redis 이벤트 스케줄 Key
     */
    public String getEventScheduleKey() {
        return buildKey(EVENT_SCHEDULE_KEY_SUFFIX);
    }


    /** 이벤트 락 키 생성
     * @param eventId 이벤트 ID
     * @return Redis 이벤트 락 Key
     */
    public String getEventLockKey(String eventId) {
        validateEventId(eventId);
        return buildKey(String.format(EVENT_LOCK_KEY_FORMAT, eventId));
    }

    /**
     * Redis 키에서  {} 해시태그 기준 eventId 추출
     * @param key Redis 키
     * @return eventId 또는 null
     */
    public String extractEventId(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        int start = key.indexOf('{');
        int end = key.indexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return key.substring(start + 1, end);
        }
        return null;
    }


    /** 이벤트 ID 유효성 검사
     * @param eventId 이벤트 ID
     */
    private void validateEventId(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("eventId는 null 또는 빈 문자열일 수 없습니다.");
        }
    }

    /** 키가 event:{id}:start_flag 형태인지 여부 확인 */
    public boolean isEventStartFlagKey(String key) {
        if (key == null || key.isBlank()) return false;
        if (!key.startsWith(envPrefix + EVENT_PREFIX + ":")) return false;
        if (!key.endsWith(":start_flag")) return false;

        int open = key.indexOf('{');
        int close = key.indexOf('}');
        return open > 0 && close > open && (close + 1 == key.lastIndexOf(":start_flag"));
    }

    // 공통 키 빌드 메서드
    private String buildKey(String suffix) {
        return envPrefix + EVENT_PREFIX + ":" + suffix;
    }
}
