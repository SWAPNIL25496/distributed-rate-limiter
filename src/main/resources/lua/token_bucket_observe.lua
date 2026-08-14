-- Token-bucket observe (read-only; never consumes). Mirrors evaluate refill math without HSET.
-- KEYS[1] = rl:v1:tb:{identifier}:{namespace}:{shardId}
-- ARGV[1] = now epoch millis
-- ARGV[2] = burst capacity (per-shard)
-- ARGV[3] = refill per second
-- Returns: { remaining, reset_at_epoch_ms, consumed }

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
end

local remaining = math.floor(tokens)
local consumed = burst - remaining
if consumed < 0 then
  consumed = 0
end

local deficit = burst - tokens
local reset_at
if deficit <= 0.0 then
  reset_at = now
else
  local seconds_to_full = deficit / refill
  reset_at = now + math.floor(seconds_to_full * 1000.0 + 0.5)
end

return { remaining, reset_at, consumed }
