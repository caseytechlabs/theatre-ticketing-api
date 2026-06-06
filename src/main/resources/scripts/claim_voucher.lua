-- KEYS[1] = voucher key
-- ARGV[1] = userId
-- ARGV[2] = pendingAt (epoch millis as string)
-- ARGV[3] = now (epoch millis as string)
local key = KEYS[1]
local status = redis.call('HGET', key, 'status')
if not status or status == false then
    return -1
end
if status ~= 'AVAILABLE' then
    if status == 'CLAIMED' then
        return -3
    end
    return -2
end
local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt'))
local now = tonumber(ARGV[3])
if expiresAt and now > expiresAt then
    redis.call('DEL', key)
    return -4
end
redis.call('HSET', key, 'status', 'PENDING_CLAIM', 'pendingUserId', ARGV[1], 'pendingAt', ARGV[2])
return 1
