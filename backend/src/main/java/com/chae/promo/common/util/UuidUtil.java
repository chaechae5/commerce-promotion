package com.chae.promo.common.util;

import java.util.UUID;

/**
 * UUID(Universally Unique Identifier) 생성 유틸리티 클래스
 */
public class UuidUtil {
    /**
     * 하이픈(-) 포함된 UUID (표준형, 36자)
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }

}
