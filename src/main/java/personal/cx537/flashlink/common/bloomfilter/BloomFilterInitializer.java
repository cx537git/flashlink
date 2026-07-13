package personal.cx537.flashlink.common.bloomfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import personal.cx537.flashlink.service.ShortLinkService;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 布隆过滤器启动初始化器。
 *
 * <p>在应用启动时从数据库加载所有有效短链码（启用且未过期），
 * 全量填充到 Guava BloomFilter 中。预期容量 100 万，误判率 0.01%。</p>
 *
 * @author Ethan Wu
 */
@Component
@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    @Autowired
    private ShortLinkService shortLinkService;

    @Override
    public void run(ApplicationArguments args) {
        BloomFilter<String> filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                1_000_000,
                0.0001f
        );

        List<String> allShortCode = shortLinkService.getAllShortCode();
        allShortCode.forEach(filter::put);
        BloomFilterHolder.initFirstFilter(filter);

        log.info("布隆过滤器全量初始化完成，已加载 {} 条短码", allShortCode.size());
    }
}