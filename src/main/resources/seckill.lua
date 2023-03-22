---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by Chu.
--- DateTime: 2023/3/15 10:19
---
--1.参数列表
--1.1优惠券id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]
--1.3订单id
local orderId = ARGV[3]

--2.数据key
local stock = "seckill:stock" .. voucherId
local order = "seckill:order" .. orderId

--3.脚本业务
--3.1 判断库存是否充足
if (tonumber(redis.call('get',stock)) <= 0) then
    return 1
end
--3.2 判断用户是否下单
if (redis.call('sismember', order, userId) == 1) then
    return 2
end
--3.3扣减库存
redis.call('incrby', stock, -1)
--userid放入set集合
redis.call('sadd', order, userId)
--3.4 下单（保存用户） sadd orderKey userId
redis.call('sadd', orderKey, userId)
--3.5 发送到消息队列
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)