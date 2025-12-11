-- tail_simple.lua
-- tail-recursive countdown returning 0
local n = tonumber(arg and arg[1]) or 10000
local function tail(n)
  if n == 0 then return 0 end
  return tail(n - 1)
end
print(tail(n))
