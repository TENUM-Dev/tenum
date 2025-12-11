-- tail_accum.lua
-- tail-recursive accumulator sum returning sum 1..n
local n = tonumber(arg and arg[1]) or 10000
local function helper(n, acc)
  if n == 0 then return acc end
  return helper(n - 1, acc + n)
end
local function sum(n)
  return helper(n, 0)
end
print(sum(n))
