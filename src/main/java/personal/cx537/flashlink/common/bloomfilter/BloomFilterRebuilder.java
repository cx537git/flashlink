package personal.cx537.flashlink.common.bloomfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import personal.cx537.flashlink.service.ShortLinkService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 布隆过滤器定时重建器。
 *
 * <h3>业务决策：每日凌晨 3:00 全量重建</h3>
 * <p>布隆过滤器不支持删除，过期/禁用短码会持续占用空间并增加误判率。
 * 每日凌晨从数据库重新加载有效短码，通过双缓冲机制平滑切换。</p>
 *
 * <h3>增量补偿</h3>
 * <p>重建过程中（从数据查询到切换完成）可能产生新短码，通过
 * {@link ShortLinkService#getShortCodesCreatedAfter} 获取增量数据，
 * 补充到 next 过滤器，确保不丢失。</p>
 *
 * @author Ethan Wu
 */
@Component
@Slf4j
public class BloomFilterRebuilder {

    @Autowired
    private ShortLinkService shortLinkService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuild() {
        log.info("开始定时重建布隆过滤器...");

        LocalDateTime startTime = LocalDateTime.now();
        List<String> validCodes = shortLinkService.getAllShortCode();
        BloomFilter<String> newFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                Math.max(validCodes.size(), 1_000_000),
                0.0001
        );
        validCodes.forEach(newFilter::put);

        BloomFilterHolder.startRebuild(newFilter);

        List<String> newCodes = shortLinkService.getShortCodesCreatedAfter(startTime);

        BloomFilter<String> next = BloomFilterHolder.getNext();
        if (next != null) {
            newCodes.forEach(next::put);
        }

        BloomFilterHolder.finishRebuild();

        log.info("布隆过滤器重建完成，有效短码数量: {}", validCodes.size());
    }
}