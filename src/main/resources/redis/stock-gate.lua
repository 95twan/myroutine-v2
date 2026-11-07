-- KEYS[i] = stock:available:{productId}, ARGV[i] = 수량. 상품 ID 순으로 같은 순서다.
local available = {}
for i = 1, #KEYS do
  local value = redis.call('GET', KEYS[i])
  if not value then
    return -1
  end
  available[i] = tonumber(value)
end
for i = 1, #KEYS do
  if available[i] < tonumber(ARGV[i]) then
    return i
  end
end
for i = 1, #KEYS do
  redis.call('DECRBY', KEYS[i], ARGV[i])
end
return 0
