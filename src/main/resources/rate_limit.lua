-- KEYS[1] 限流 key
-- ARGV[1] 当前时间戳(毫秒) ARGV[2] 窗口(毫秒) ARGV[3] 阈值 ARGV[4] 本次请求标识
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

-- 1. 清掉窗口外的记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
-- 2. 数窗口内还有几个
local count = redis.call('ZCARD', key)
-- 3. 超了就拒绝
if count >= limit then
    return 0
end
-- 4. 记录本次 + 续期
redis.call('ZADD', key, now, member)
redis.call('PEXPIRE', key, window)
return 1