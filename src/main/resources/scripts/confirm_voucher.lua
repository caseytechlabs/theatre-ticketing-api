-- KEYS[1] = voucher key
-- ARGV[1] = paymentSuccess ('true'/'false')
-- ARGV[2] = timestamp (epoch millis as string)
local key = KEYS[1]
local status = redis.call('HGET', key, 'status')
if not status or status == false then
    return -1
end
if status ~= 'PENDING_CLAIM' then
    return -2
end
if ARGV[1] == 'true' then
    redis.call('HSET', key, 'status', 'CLAIMED', 'claimedAt', ARGV[2])
    redis.call('PERSIST', key)
else
    redis.call('HSET', key, 'status', 'AVAILABLE')
    redis.call('HDEL', key, 'pendingUserId', 'pendingAt')
end
return 1
