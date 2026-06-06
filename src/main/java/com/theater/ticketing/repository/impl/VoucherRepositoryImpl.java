package com.theater.ticketing.repository.impl;

import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class VoucherRepositoryImpl implements VoucherRepository {

    private static final String KEY_PREFIX = "theater:voucher:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> claimVoucherScript;
    private final RedisScript<Long> confirmVoucherScript;

    private String key(String voucherId) {
        return KEY_PREFIX + voucherId;
    }

    @Override
    public Voucher save(Voucher voucher, long ttlSeconds) {
        String key = key(voucher.getId());
        Map<String, String> hash = new HashMap<>();
        hash.put("id", voucher.getId());
        hash.put("status", voucher.getStatus().name());
        if (voucher.getCustomerId() != null) hash.put("customerId", voucher.getCustomerId());
        if (voucher.getPendingUserId() != null) hash.put("pendingUserId", voucher.getPendingUserId());
        if (voucher.getCreatedAt() != null) hash.put("createdAt", String.valueOf(voucher.getCreatedAt().toEpochMilli()));
        if (voucher.getExpiresAt() != null) hash.put("expiresAt", String.valueOf(voucher.getExpiresAt().toEpochMilli()));
        if (voucher.getClaimedAt() != null) hash.put("claimedAt", String.valueOf(voucher.getClaimedAt().toEpochMilli()));
        if (voucher.getPendingAt() != null) hash.put("pendingAt", String.valueOf(voucher.getPendingAt().toEpochMilli()));

        redisTemplate.opsForHash().putAll(key, hash);
        if (ttlSeconds > 0) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return voucher;
    }

    @Override
    public Optional<Voucher> findById(String voucherId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(voucherId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToVoucher(entries));
    }

    @Override
    public List<Voucher> findAll() {
        // NOTE: KEYS is not for high-cardinality production use; prefer SCAN for large datasets
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(k -> redisTemplate.opsForHash().entries(k))
                .filter(entries -> !entries.isEmpty())
                .map(this::mapToVoucher)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String voucherId) {
        redisTemplate.delete(key(voucherId));
    }

    @Override
    public long claimVoucher(String voucherId, String userId, Instant pendingAt, Instant now) {
        Long result = redisTemplate.execute(
                claimVoucherScript,
                Collections.singletonList(key(voucherId)),
                userId,
                String.valueOf(pendingAt.toEpochMilli()),
                String.valueOf(now.toEpochMilli())
        );
        return result != null ? result : -99L;
    }

    @Override
    public long confirmVoucher(String voucherId, boolean paymentSuccess, Instant timestamp) {
        Long result = redisTemplate.execute(
                confirmVoucherScript,
                Collections.singletonList(key(voucherId)),
                String.valueOf(paymentSuccess),
                String.valueOf(timestamp.toEpochMilli())
        );
        return result != null ? result : -99L;
    }

    private Voucher mapToVoucher(Map<Object, Object> entries) {
        return Voucher.builder()
                .id((String) entries.get("id"))
                .status(VoucherStatus.valueOf((String) entries.get("status")))
                .customerId((String) entries.get("customerId"))
                .pendingUserId((String) entries.get("pendingUserId"))
                .createdAt(parseInstant((String) entries.get("createdAt")))
                .expiresAt(parseInstant((String) entries.get("expiresAt")))
                .claimedAt(parseInstant((String) entries.get("claimedAt")))
                .pendingAt(parseInstant((String) entries.get("pendingAt")))
                .build();
    }

    private Instant parseInstant(String millis) {
        if (millis == null) return null;
        return Instant.ofEpochMilli(Long.parseLong(millis));
    }
}
