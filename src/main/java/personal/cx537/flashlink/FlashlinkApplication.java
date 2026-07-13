package personal.cx537.flashlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlashLink 短链接服务启动类。
 *
 * <p>核心功能：将长链接转换为短链接，通过短链码实现302跳转，同时完成点击统计。
 * 架构上采用 Redis 缓存 + 布隆过滤器 + Redis Stream 异步消费的组合方案，
 * 以应对高并发场景下的缓存穿透、缓存雪崩和数据一致性问题。
 *
 * @author Ethan Wu
 */
@SpringBootApplication
@EnableScheduling
public class FlashlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashlinkApplication.class, args);
    }

}