-- KEYS[1] = auth:session:{id}, KEYS[2] = auth:member-sessions:{memberId}, ARGV[1] = expectedHash, ARGV[2] = newHash, ARGV[3] = ttlSeconds
if redis.call('HGET', KEYS[1], 'refreshHash') == ARGV[1] then
  redis.call('HSET', KEYS[1], 'refreshHash', ARGV[2])
  redis.call('EXPIRE', KEYS[1], ARGV[3])
  redis.call('EXPIRE', KEYS[2], ARGV[3])
  return 1
end
return 0
