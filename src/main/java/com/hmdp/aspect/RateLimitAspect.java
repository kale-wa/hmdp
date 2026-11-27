package com.hmdp.aspect;

import com.hmdp.annotation.RateLimit;
import com.hmdp.exception.RateLimitException;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 按用户维度限流；取不到用户就归到 anon
        String dimension = UserHolder.getUser() == null
                ? "anon" : String.valueOf(UserHolder.getUser().getId());

        String key = rateLimit.prefix() + ":"
                + joinPoint.getSignature().getDeclaringType().getSimpleName() + ":"
                + joinPoint.getSignature().getName() + ":"
                + dimension;

        long now = System.currentTimeMillis();
        String member = now + ":" + ThreadLocalRandom.current().nextInt(1000);

        Long allowed = stringRedisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(rateLimit.seconds() * 1000L),
                String.valueOf(rateLimit.count()),
                member);

        if (allowed == null || allowed == 0L) {
            log.warn("[限流] 拦截请求, key={}", key);
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }
}