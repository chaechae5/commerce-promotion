package com.chae.promo.event.redis;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class EventRedisKeyManagerTest {

    // envPrefix 보정 테스트
    @Nested
    @DisplayName("Prefix 보정 테스트")
    class PrefixCorrectionTest {

        @Test
        @DisplayName("콜론이 없으면 자동으로 콜론이 추가되어야 한다")
        void testPrefixWithoutColon() {
            EventRedisKeyManager manager = new EventRedisKeyManager("dev");
            String key = manager.getEventStartFlagKey("E123");
            assertThat(key).isEqualTo("dev:event:{E123}:start_flag");
        }

        @Test
        @DisplayName("콜론이 이미 있으면 그대로 유지되어야 한다")
        void testPrefixWithColon() {
            EventRedisKeyManager manager = new EventRedisKeyManager("dev:");
            String key = manager.getEventStartFlagKey("E123");
            assertThat(key).isEqualTo("dev:event:{E123}:start_flag");
        }

        @Test
        @DisplayName("빈 prefix는 그대로 사용해야 한다")
        void testEmptyPrefix() {
            EventRedisKeyManager manager = new EventRedisKeyManager("");
            String key = manager.getEventStartFlagKey("E123");
            assertThat(key).isEqualTo("event:{E123}:start_flag");
        }

        @Test
        @DisplayName("null prefix는 안전하게 빈 문자열로 처리되어야 한다")
        void testNullPrefix() {
            EventRedisKeyManager manager = new EventRedisKeyManager(null);
            String key = manager.getEventStartFlagKey("E123");
            assertThat(key).isEqualTo("event:{E123}:start_flag");
        }
    }
    private EventRedisKeyManager keyManager = new EventRedisKeyManager("dev:");


    @Test
    @DisplayName("이벤트 시작 플래그 키 생성이 올바르게 동작해야 한다")
    void testGetEventStartFlagKey() {
        String key = keyManager.getEventStartFlagKey("E123");
        assertThat(key).isEqualTo("dev:event:{E123}:start_flag");
    }

    @Test
    @DisplayName("이벤트 상태 키 생성이 올바르게 동작해야 한다")
    void testGetEventStatusKey() {
        String key = keyManager.getEventStatusKey("E123");
        assertThat(key).isEqualTo("dev:event:{E123}:status");
    }

    @Test
    @DisplayName("이벤트 스케줄 키 생성이 올바르게 동작해야 한다")
    void testGetEventScheduleKeyFormat() {
        String key = keyManager.getEventScheduleKey();
        assertThat(key).isEqualTo("dev:event:schedule");
    }

    @Test
    @DisplayName("eventId가 null일 경우 IllegalArgumentException 발생")
    void testValidateEventId_null() {
        assertThatThrownBy(() -> keyManager.getEventStatusKey(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId는 null 또는 빈 문자열일 수 없습니다.");
    }

    @Test
    @DisplayName("eventId가 공백일 경우 IllegalArgumentException 발생")
    void testValidateEventId_blank() {
        assertThatThrownBy(() -> keyManager.getEventStartFlagKey("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Redis 키에서 eventId를 정상적으로 추출해야 한다")
    void testExtractEventId() {
        String eventId = keyManager.extractEventId("dev:event:{E123}:start_flag");
        assertThat(eventId).isEqualTo("E123");
    }

    @Test
    @DisplayName("해시태그 구분자가 없으면 null 반환")
    void testExtractEventId_noCurlyBraces() {
        String eventId = keyManager.extractEventId("dev:event:E123:start_flag");
        assertThat(eventId).isNull();
    }

    @Test
    @DisplayName("key가 null이면 null 반환")
    void testExtractEventId_nullKey() {
        assertThat(keyManager.extractEventId(null)).isNull();
    }

    @Test
    @DisplayName("정상적인 start_flag 키는 true를 반환해야 한다")
    void testIsEventStartFlagKey_validKeys() {
        assertThat(keyManager.isEventStartFlagKey("dev:event:{E1001}:start_flag")).isTrue();
        assertThat(keyManager.isEventStartFlagKey("dev:event:{abc123}:start_flag")).isTrue();
    }

    @Test
    @DisplayName("잘못된 키 형식은 false를 반환해야 한다")
    void testIsEventStartFlagKey_invalidKeys() {
        // {} 누락
        assertThat(keyManager.isEventStartFlagKey("dev:event:E1001:start_flag")).isFalse();

        // suffix 누락
        assertThat(keyManager.isEventStartFlagKey("dev:event:{E1001}:status")).isFalse();

        // prefix 누락
        assertThat(keyManager.isEventStartFlagKey("random:event:{E1001}:start_flag")).isFalse();

        // 잘못된 구조
        assertThat(keyManager.isEventStartFlagKey("dev:event:{E1001}:start_flag:extra")).isFalse();

        // 중괄호 짝 안맞음
        assertThat(keyManager.isEventStartFlagKey("dev:event:{E1001:start_flag")).isFalse();

        // null, 공백
        assertThat(keyManager.isEventStartFlagKey(null)).isFalse();
        assertThat(keyManager.isEventStartFlagKey("")).isFalse();
        assertThat(keyManager.isEventStartFlagKey(" ")).isFalse();
    }

    @Test
    @DisplayName("환경 prefix가 달라도 event:{id}:start_flag 형태면 false를 반환해야 한다")
    void testIsEventStartFlagKey_wrongEnvPrefix() {
        // envPrefix가 "dev:"이므로 "prod:event:{E1001}:start_flag"는 false
        assertThat(keyManager.isEventStartFlagKey("prod:event:{E1001}:start_flag")).isFalse();
    }

    @Test
    @DisplayName("envPrefix가 비어 있을 경우에도 정상 작동해야 한다")
    void testIsEventStartFlagKey_emptyPrefix() {
        EventRedisKeyManager noPrefixManager = new EventRedisKeyManager("");
        assertThat(noPrefixManager.isEventStartFlagKey("event:{E1001}:start_flag")).isTrue();
    }

    @Test
    @DisplayName("이벤트 락 키 생성이 올바르게 동작해야 한다")
    void testGetEventLockKey() {
        EventRedisKeyManager keyManager = new EventRedisKeyManager("dev:");
        String key = keyManager.getEventLockKey("E123");
        assertThat(key).isEqualTo("dev:event:lock:{E123}");
    }
}
