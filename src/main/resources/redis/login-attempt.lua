-- KEYS[1] = auth:login-fail:{email}
local count = tonumber(redis.call('GET', KEYS[1]) or '0')
if count >= 5 then
  return -1
end
count = redis.call('INCR', KEYS[1])
if count == 1 or count == 5 or redis.call('TTL', KEYS[1]) < 0 then
  redis.call('EXPIRE', KEYS[1], 900)
end
return count
