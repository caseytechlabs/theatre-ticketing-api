-- Atomically transitions a voucher from AVAILABLE → PENDING_CLAIM.
-- Running inside Redis as a Lua script guarantees no other command can
-- interleave between the status read and the write — this is the core
-- fraud-prevention mechanism against concurrent double-claim attempts.
--
-- KEYS[1] = voucher hash key  (e.g. "theater:voucher:<id>")
-- ARGV[1] = userId claiming the voucher
-- ARGV[2] = pendingAt timestamp (epoch millis)
-- ARGV[3] = now timestamp (epoch millis)
-- ARGV[4] = pendingTtlMs — how long a PENDING_CLAIM is valid (millis)
--
-- Return codes:
--   1  success — voucher moved to PENDING_CLAIM
--  -1  not found
--  -2  already PENDING_CLAIM by someone else and not yet expired
--  -3  already CLAIMED (permanently used)
--  -4  expired — key deleted inline

local key   = KEYS[1]
local now   = tonumber(ARGV[3])
local ttlMs = tonumber(ARGV[4])
local status = redis.call('HGET', key, 'status')

if not status or status == false then
    return -1
end

if status == 'CLAIMED' then
    return -3
end

-- If a previous PENDING_CLAIM exists, check whether it has timed out.
-- If it has, auto-revert so the new caller can proceed without a scheduler.
if status == 'PENDING_CLAIM' then
    local pendingAt = tonumber(redis.call('HGET', key, 'pendingAt'))
    if pendingAt and (now - pendingAt) < ttlMs then
        return -2  -- still within TTL window — another user holds the claim
    end
    -- TTL has passed — clear stale fields and fall through to re-claim
    redis.call('HDEL', key, 'pendingUserId', 'pendingAt')
    redis.call('HSET', key, 'status', 'AVAILABLE')
end

-- Inline expiry check: if the voucher's own expiresAt has passed, purge it.
local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt'))
if expiresAt and now > expiresAt then
    redis.call('DEL', key)
    return -4
end

-- All checks passed — atomically stamp the reservation fields.
redis.call('HSET', key, 'status', 'PENDING_CLAIM', 'pendingUserId', ARGV[1], 'pendingAt', ARGV[2])
return 1
