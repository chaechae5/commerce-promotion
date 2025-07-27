package com.chae.promo.coupon.event;

/**
 * Kafka 토픽을 관리하는 Enum.
 * 각 Enum 상수는 실제 토픽 이름을 값으로 가집니다.
 */
public enum Topic {
    COUPON_ISSUED(Names.COUPON_ISSUED); // 내부 Names 클래스의 상수를 사용

    private final String topicName;

    Topic(String topicName) {
        this.topicName = topicName;
    }

    public String getName() {
        return this.topicName;
    }

    /**
     * @KafkaListener 어노테이션에서 사용하기 위한
     * 컴파일 타임 상수 문자열을 정의하는 내부 클래스.
     */
    public static final class Names {
        public static final String COUPON_ISSUED = "coupon.issued";
    }
}