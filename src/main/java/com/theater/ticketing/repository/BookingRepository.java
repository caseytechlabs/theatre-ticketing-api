package com.theater.ticketing.repository;

import com.theater.ticketing.model.Booking;
import com.theater.ticketing.model.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class BookingRepository {

    private static final String KEY_PREFIX = "theater:booking:";
    private static final String ALL_SET    = "theater:bookings";

    private final StringRedisTemplate redisTemplate;

    private String key(String bookingId) {
        return KEY_PREFIX + bookingId;
    }

    public Booking save(Booking booking) {
        String key = key(booking.getId());
        Map<String, String> hash = new HashMap<>();
        hash.put("id", booking.getId());
        hash.put("voucherId", booking.getVoucherId());
        hash.put("userId", booking.getUserId());
        hash.put("status", booking.getStatus().name());
        if (booking.getCustomerId() != null) hash.put("customerId", booking.getCustomerId());
        if (booking.getCreatedAt() != null) hash.put("createdAt", String.valueOf(booking.getCreatedAt().toEpochMilli()));
        if (booking.getUpdatedAt() != null) hash.put("updatedAt", String.valueOf(booking.getUpdatedAt().toEpochMilli()));
        if (booking.getVoucherExpiresAt() != null) hash.put("voucherExpiresAt", String.valueOf(booking.getVoucherExpiresAt().toEpochMilli()));
        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.opsForSet().add(ALL_SET, booking.getId());
        return booking;
    }

    public List<Booking> findAll() {
        Set<String> ids = redisTemplate.opsForSet().members(ALL_SET);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public Optional<Booking> findById(String bookingId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(bookingId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToBooking(entries));
    }

    public void delete(String bookingId) {
        redisTemplate.opsForSet().remove(ALL_SET, bookingId);
        redisTemplate.delete(key(bookingId));
    }

    public void updateStatus(String bookingId, BookingStatus status, Instant updatedAt) {
        redisTemplate.opsForHash().putAll(key(bookingId), Map.of(
                "status", status.name(),
                "updatedAt", String.valueOf(updatedAt.toEpochMilli())
        ));
    }

    private Booking mapToBooking(Map<Object, Object> entries) {
        return Booking.builder()
                .id((String) entries.get("id"))
                .voucherId((String) entries.get("voucherId"))
                .userId((String) entries.get("userId"))
                .customerId((String) entries.get("customerId"))
                .status(BookingStatus.valueOf((String) entries.get("status")))
                .createdAt(RedisUtils.parseInstant((String) entries.get("createdAt")))
                .updatedAt(RedisUtils.parseInstant((String) entries.get("updatedAt")))
                .voucherExpiresAt(RedisUtils.parseInstant((String) entries.get("voucherExpiresAt")))
                .build();
    }
}
