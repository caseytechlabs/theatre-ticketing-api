package com.theater.ticketing.repository;

import java.time.Instant;

class RedisUtils {
    static Instant parseInstant(String millis) {
        if (millis == null) return null;
        return Instant.ofEpochMilli(Long.parseLong(millis));
    }
}
