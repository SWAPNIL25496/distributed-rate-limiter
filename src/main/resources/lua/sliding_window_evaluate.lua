-- Counter-based sliding-window evaluate (consume one). Mirrors SlidingWindowEngine.
-- KEYS[1] = rl:v1:sw:{identifier}:{namespace}:{shardId}
-- ARGV[1] = now epoch seconds
-- ARGV[2] = limit (per-shard)
-- ARGV[3] = window seconds
-- Returns: { allowed (0|1), remaining, reset_at_epoch_ms }

local key = KEYS[1]
local now = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local window = tonumber(ARGV[3])

local function aligned_window_start(epoch_sec)
  return epoch_sec - (epoch_sec % window)
end

local data = redis.call('HMGET', key, 'window_start', 'previous', 'current')
local window_start
local previous
local current

if data[1] == false then
  window_start = aligned_window_start(now)
  previous = 0
  current = 0
else
  window_start = tonumber(data[1])
  previous = tonumber(data[2])
  current = tonumber(data[3])
end

local aligned = aligned_window_start(now)
if aligned ~= window_start then
  if aligned == window_start + window then
    previous = current
  else
    previous = 0
  end
  current = 0
  window_start = aligned
end

local elapsed_in_window = now - window_start
local weight = 1.0 - (elapsed_in_window / window)
if weight < 0.0 then
  weight = 0.0
end
if weight > 1.0 then
  weight = 1.0
end

local estimated = previous * weight + current
local allowed = 0
if estimated + 1.0 <= limit then
  current = current + 1
  allowed = 1
end

estimated = previous * weight + current
local remaining = math.floor(limit - estimated)
if remaining < 0 then
  remaining = 0
end

redis.call(
  'HSET',
  key,
  'window_start',
  tostring(window_start),
  'previous',
  tostring(previous),
  'current',
  tostring(current)
)

local reset_at_ms = (window_start + window) * 1000
return { allowed, remaining, reset_at_ms }
