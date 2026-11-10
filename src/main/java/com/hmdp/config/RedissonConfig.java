package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private String port;

    // 课程里是 ${spring.redis.password}，但你的 application.yaml 没配 password，会启动失败，
    // 所以这里加了默认空值；配了密码就直接从配置文件读
    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        // 配置类
        Config config = new Config();
        // 添加redis地址，这里添加了单点的地址，也可以使用config.useClusterServers()添加集群地址
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
        }
        // 创建客户端
        return Redisson.create(config);
    }
}
