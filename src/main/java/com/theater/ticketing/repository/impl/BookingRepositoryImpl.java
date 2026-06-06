package com.theater.ticketing.repository.impl;

import com.theater.ticketing.model.Booking;
import com.theater.ticketing.model.BookingStatus;
import com.theater.ticketing.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepository {

    private static final String KEY_PREFIX = "theater:booking:";

    private final StringRedisTemplate redisTemplate;

    private String key(String bookingId) {
        return KEY_PREFIX + bookingId;
    }

    @Override
    public Booking save(Booking booking) {
        String key = key(booking.getId());
        Map<String, String> hash = new HashMap<>();
        hash.put("id", booking.getId());
        hash.put("voucherId", booking.getVoucherId());
        hash.put("userId", booking.getUserId());
        hash.put("status", booking.getStatus().name());
        if (booking.getCreatedAt() != null) hash.put("createdAt", String.valueOf(booking.getCreatedAt().toEpochMilli()));
        if (booking.getUpdatedAt() != null) hash.put("updatedAt", String.valueOf(booking.getUpdatedAt().toEpochMilli()));
        redisTemplate.opsForHash().putAll(key, hash);
        return booking;
    }

    @Override
    public Optional<Booking> findById(String bookingId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(bookingId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToBooking(entries));
    }

    @Override
    public void updateStatus(String bookingId, BookingStatus status, Instant updatedAt) {
        String key = key(bookingId);
        redisTemplate.opsForHash().put(key, "status", status.name());
        redisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(updatedAt.toEpochMilli()));
    }

    private Booking mapToBooking(Map<Object, Object> entries) {
        return Booking.builder()
                .id((String) entries.get("id"))
                .voucherId((String) entries.get("voucherId"))
                .userId((String) entries.get("userId"))
                .status(BookingStatus.valueOf((String) entries.get("status")))
                .createdAt(parseInstant((String) entries.get("createdAt")))
                .updatedAt(parseInstant((String) entries.get("updatedAt")))
                .build();
    }

    private Instant parseInstant(String millis) {
        if (millis == null) return null;
        return Instant.ofEpochMilli(Long.parseLong(millis));
    }
}
