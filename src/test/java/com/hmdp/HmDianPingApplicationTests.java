package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /* ==================== P48-P49 全局唯一ID ==================== */

    @Test
    void testNextId() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(300);
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    /* ==================== P58-P63 分布式锁 ==================== */

    @Test
    void testLock() throws InterruptedException {
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + 1, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(1200);
        // 判断是否获取锁成功
        if (!isLock) {
            // 获取锁失败，返回错误或重试
            log.error("不允许重复下单！");
            return;
        }
        try {
            // 获取锁成功，执行业务
            log.info("获取锁成功，执行业务");
            Thread.sleep(100);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /* ==================== P64-P68 Redisson ==================== */

    @Test
    void testRedisson() throws InterruptedException {
        // 获取锁（可重入），指定锁的名称
        RLock lock = redissonClient.getLock("lock:order:" + 1);
        // 尝试获取锁，参数分别是：获取锁的最大等待时间（期间会重试），锁自动释放时间，时间单位
        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        // 判断释放获取成功
        if (isLock) {
            try {
                log.info("获取锁成功，执行业务");
            } finally {
                // 释放锁
                lock.unlock();
            }
        }
    }

    /* ==================== P73 基于List的消息队列 ==================== */

    @Test
    void testListQueue() throws InterruptedException {
        String queueName = "queue:list";
        stringRedisTemplate.delete(queueName);

        // 生产者
        new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                stringRedisTemplate.opsForList().leftPush(queueName, "message_" + i);
                try {
                    TimeUnit.MILLISECONDS.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();

        // 消费者
        while (true) {
            String message = stringRedisTemplate.opsForList().rightPop(queueName, 2, TimeUnit.SECONDS);
            if (message == null) {
                log.info("队列已空");
                break;
            }
            log.info("消费者消费到消息：{}", message);
        }
    }

    /* ==================== P74 基于PubSub的消息队列 ==================== */

    @Test
    void testPubSub() throws InterruptedException {
        String channel = "order";

        // 订阅者
        new Thread(() -> stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.subscribe(
                    (message, pattern) -> log.info("收到消息：{}", new String(message.getBody(), StandardCharsets.UTF_8)),
                    channel.getBytes(StandardCharsets.UTF_8));
            return null;
        }), "subscriber").start();

        TimeUnit.MILLISECONDS.sleep(300);

        // 发布者
        for (int i = 1; i <= 3; i++) {
            stringRedisTemplate.convertAndSend(channel, "hello_" + i);
            TimeUnit.MILLISECONDS.sleep(200);
        }

        TimeUnit.MILLISECONDS.sleep(500);
    }

    /* ==================== P75-P77 基于Stream的消息队列 ==================== */

    @Test
    void testStream() {
        String key = "stream.orders";
        // 发送消息
        Map<String, String> map = new HashMap<>();
        map.put("userId", "1010");
        map.put("voucherId", "1");
        stringRedisTemplate.opsForStream().add(key, map);

        // 读取消息（单消费模式，0表示从头读）
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .read(StreamOffset.create(key, ReadOffset.from("0")));
        if (records != null) {
            for (MapRecord<String, Object, Object> record : records) {
                log.info("id = {}, value = {}", record.getId(), record.getValue());
            }
        }
    }

    @Test
    void testStreamGroup() {
        String key = "stream.orders";
        String group = "g1";
        String consumer = "c1";
        // 创建消费者组
        try {
            stringRedisTemplate.opsForStream().createGroup(key, ReadOffset.from("0"), group);
        } catch (Exception e) {
            log.warn("消费者组已存在：{}", e.getMessage());
        }

        // 消费者组读取消息
        while (true) {
            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                    StreamOffset.create(key, ReadOffset.lastConsumed())
            );
            if (records == null || records.isEmpty()) {
                log.info("没有新消息了");
                break;
            }
            for (MapRecord<String, Object, Object> record : records) {
                log.info("消费到消息：id = {}, value = {}", record.getId(), record.getValue());
                // 确认消息
                stringRedisTemplate.opsForStream().acknowledge(key, group, record.getId());
            }
        }
    }
}
