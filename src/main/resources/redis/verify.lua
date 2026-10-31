-- KEYS[1] = auth:email-code:{purpose}:{email}, ARGV[1] = 입력 코드의 해시
local stored = redis.call('HGET', KEYS[1], 'codeHash')
if not stored then
  return 0
end
if stored == ARGV[1] then
  redis.call('DEL', KEYS[1])
  return 1
end
if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= 5 then
  redis.call('DEL', KEYS[1])
end
return 0
