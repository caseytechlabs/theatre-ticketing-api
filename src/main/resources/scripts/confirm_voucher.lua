-- Atomically resolves a PENDING_CLAIM voucher based on payment outcome.
-- Called by the client confirming payment (success) or by cancelIfExpired
-- reverting an abandoned reservation (failure).
--
-- KEYS[1] = voucher hash key  (e.g. "theater:voucher:<id>")
-- ARGV[1] = paymentSuccess ('true' / 'false')
-- ARGV[2] = timestamp (epoch millis)
--
-- Return codes:
--   1  success
--  -1  not found (key expired or already deleted)
--  -2  not in PENDING_CLAIM state (idempotency guard)

local key = KEYS[1]
local status = redis.call('HGET', key, 'status')

if not status or status == false then
    return -1
end

-- Only act on PENDING_CLAIM; any other state means this call is a no-op
-- (e.g. scheduler already reverted while payment was in-flight).
if status ~= 'PENDING_CLAIM' then
    return -2
end

if ARGV[1] == 'true' then
    -- Payment succeeded: stamp CLAIMED and call PERSIST so the key is never
    -- auto-evicted by the TTL that was set at voucher creation time.
    redis.call('HSET', key, 'status', 'CLAIMED', 'claimedAt', ARGV[2])
    redis.call('PERSIST', key)
else
    -- Payment failed or timed out: revert to AVAILABLE and scrub the
    -- reservation fields so the voucher is immediately re-claimable.
    redis.call('HSET', key, 'status', 'AVAILABLE')
    redis.call('HDEL', key, 'pendingUserId', 'pendingAt')
end
return 1