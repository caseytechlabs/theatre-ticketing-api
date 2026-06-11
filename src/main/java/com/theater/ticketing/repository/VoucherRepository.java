package com.theater.ticketing.repository;

import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class VoucherRepository {

    private static final String KEY_PREFIX = "theater:voucher:";
    private static final String ALL_SET    = "theater:vouchers";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> claimVoucherScript;
    private final RedisScript<Long> confirmVoucherScript;

    private String key(String voucherId) {
        return KEY_PREFIX + voucherId;
    }

    public Voucher save(Voucher voucher, long ttlSeconds) {
        String key = key(voucher.getId());
        Map<String, String> hash = new HashMap<>();
        hash.put("id", voucher.getId());
        hash.put("status", voucher.getStatus().name());
        // customerId is absent for universal vouchers — null check avoids storing "null" as a string
        if (voucher.getCustomerId() != null) hash.put("customerId", voucher.getCustomerId());
        if (voucher.getPendingUserId() != null) hash.put("pendingUserId", voucher.getPendingUserId());
        if (voucher.getCreatedAt() != null) hash.put("createdAt", String.valueOf(voucher.getCreatedAt().toEpochMilli()));
        if (voucher.getExpiresAt() != null) hash.put("expiresAt", String.valueOf(voucher.getExpiresAt().toEpochMilli()));
        if (voucher.getClaimedAt() != null) hash.put("claimedAt", String.valueOf(voucher.getClaimedAt().toEpochMilli()));
        if (voucher.getPendingAt() != null) hash.put("pendingAt", String.valueOf(voucher.getPendingAt().toEpochMilli()));

        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.opsForSet().add(ALL_SET, voucher.getId());
        if (ttlSeconds > 0) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return voucher;
    }

    public Optional<Voucher> findById(String voucherId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(voucherId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToVoucher(entries));
    }

    public List<Voucher> findAll() {
        Set<String> ids = redisTemplate.opsForSet().members(ALL_SET);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public void delete(String voucherId) {
        redisTemplate.opsForSet().remove(ALL_SET, voucherId);
        redisTemplate.delete(key(voucherId));
    }

    // Executes claim_voucher.lua atomically. The Lua script is the single point
    // where AVAILABLE → PENDING_CLAIM happens; no Java-side read-then-write.
    // pendingTtlMs is passed to the script so it can auto-release an expired
    // PENDING_CLAIM inline — no background scheduler required.
    public long claimVoucher(String voucherId, String userId, Instant pendingAt, Instant now, long pendingTtlMs) {
        Long result = redisTemplate.execute(
                claimVoucherScript,
                Collections.singletonList(key(voucherId)),
                userId,
                String.valueOf(pendingAt.toEpochMilli()),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(pendingTtlMs)
        );
        return result != null ? result : -99L;
    }

    // Executes confirm_voucher.lua atomically. Used for both successful payment
    // (paymentSuccess=true → CLAIMED) and revert paths (false → AVAILABLE).
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
                .createdAt(RedisUtils.parseInstant((String) entries.get("createdAt")))
                .expiresAt(RedisUtils.parseInstant((String) entries.get("expiresAt")))
                .claimedAt(RedisUtils.parseInstant((String) entries.get("claimedAt")))
                .pendingAt(RedisUtils.parseInstant((String) entries.get("pendingAt")))
                .build();
    }
}
