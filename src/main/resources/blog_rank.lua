-- 点赞排行 Lua 脚本：原子性完成 点赞/取消点赞 + ZSet 排行榜更新
--
-- KEYS[1] = 笔记已点赞用户集合 key   blog:liked:{blogId}
-- KEYS[2] = 排行榜 ZSet key           blog:rank:likes
-- ARGV[1] = 用户 ID
-- ARGV[2] = 笔记 ID
--
-- 返回值：1 = 点赞成功（isLiked=true）  -1 = 取消点赞（isLiked=false）

local isMember = redis.call('sismember', KEYS[1], ARGV[1])
if isMember == 1 then
    redis.call('srem', KEYS[1], ARGV[1])
    redis.call('zincrby', KEYS[2], -1, ARGV[2])
    return -1
else
    redis.call('sadd', KEYS[1], ARGV[1])
    redis.call('zincrby', KEYS[2], 1, ARGV[2])
    return 1
end
