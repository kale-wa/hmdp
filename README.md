# 黑马点评（课程项目实践）

基于黑马程序员《Redis 实战》课程项目的学习实践，实现了本地生活类应用的核心后端功能。

## 技术栈

Spring Boot · MyBatis-Plus · MySQL · Redis · Redisson · RabbitMQ

## 已实现

- 短信登录 + JWT 双拦截器
- 商户缓存：封装 CacheClient，处理空值缓存（穿透）与逻辑过期（热点 Key）
- 优惠券秒杀：Redis 分布式锁 → Redisson 可重入锁 + 看门狗；Lua 原子扣减库存
- 异步下单：RabbitMQ + 手动 ACK + Redis 幂等 + 死信队列
- 达人探店、点赞、好友关注、附近商户（GEO）、用户签到（BitMap）

## 我的改造

### 2026-11-26 异步下单从 Redis Stream 改为 RabbitMQ

- **动机**：Stream 需要自己维护消费组和 PENDING，缺少死信、重试、管理台
- **实现**：Lua 只做资格判断与扣库存，Java 发送消息；消费端手动 ACK + Redis SETNX 幂等
- **代价**：扣库存与发消息不再原子，需要本地消息表或定时对账兜底
- **验证**：两次下单均落库，队列 ready/unacked 为 0，库存 100 → 98

### 2026-11-25 修复 Stream 消费确认问题

- 问题：`XACK` 的 stream 名与 `XREADGROUP` 不一致，消息永远确认不掉；消费组未创建导致 `NOGROUP`
- 修复：统一 stream 名；启动前用 `XGROUP CREATE ... MKSTREAM` 创建消费组

## 说明

课程跟练项目，代码用于学习与面试复盘；改造部分见 commit 记录。