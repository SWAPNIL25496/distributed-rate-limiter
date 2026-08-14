-- Token-bucket evaluate (consume one). Mirrors TokenBucketEngine.
-- KEYS[1] = rl:v1:tb:{identifier}:{namespace}:{shardId}
-- ARGV[1] = now epoch millis
-- ARGV[2] = burst capacity (per-shard)
-- ARGV[3] = refill per second
-- Returns: { allowed (0|1), remaining, reset_at_epoch_ms }

local key = KEYS[1]
local now = tonumber(ARGV[1])
local burst = tonumber(ARGV[2])
local refill = tonumber(ARGV[3])

local data = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local tokens
local last

if data[1] == false then
  tokens = burst
  last = now
else
  tokens = tonumber(data[1])
  last = tonumber(data[2])
end

local elapsed = now - last
if elapsed > 0 then
  tokens = math.min(burst, tokens + (elapsed / 1000.0) * refill)
  last = now
end

local allowed = 0
if tokens >= 1.0 then
  tokens = tokens - 1.0
  allowed = 1
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'last_refill_ms', tostring(last))

local remaining = math.floor(tokens)
local deficit = burst - tokens
local reset_at
if deficit <= 0.0 then
  reset_at = now
else
  local seconds_to_full = deficit / refill
  -- Match Java Math.round(seconds * 1e9) expressed as epoch millis delta.
  reset_at = now + math.floor(seconds_to_full * 1000.0 + 0.5)
end

return { allowed, remaining, reset_at }
