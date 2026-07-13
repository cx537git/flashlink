package personal.cx537.flashlink.common.consumer;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import personal.cx537.flashlink.common.constant.RedisStreamConstants;
import personal.cx537.flashlink.service.ShortLinkService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redis Stream 点击事件消费者。
 *
 * <h3>架构决策：异步解耦点击计数</h3>
 * <p>短链跳转时主链路仅做 302 重定向，点击事件通过 Redis Stream 异步发送，
 * 由独立消费者批量聚合后写入数据库。避免每次跳转都触发数据库写操作，提升吞吐。</p>
 *
 * <h3>可靠性保障</h3>
 * <ul>
 *   <li><b>消费者组</b>：保证消息至少被消费一次（At-Least-Once）</li>
 *   <li><b>PEL 认领</b>：每分钟扫描 Pending Entries List，认领超过 30 秒未 ACK 的消息</li>
 *   <li><b>批量处理</b>：每批 500 条，按 shortCode 聚合后批量更新数据库</li>
 *   <li><b>Stream 裁剪</b>：每 30 分钟检查，超过 10 万条时裁剪保留最近 10 万条</li>
 * </ul>
 *
 * @author Ethan Wu
 */
@Slf4j
@Component
public class ClickEventConsumer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShortLinkService shortLinkService;

    private static final long TRIM_THRESHOLD = 100_000L;
    private static final long TRIM_RETAIN = 100_000L;
    private static final int BATCH_SIZE = 500;
    private static final long PENDING_IDLE_MS = 30_000;

    @PostConstruct
    public void init() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    RedisStreamConstants.CLICK_STREAM_KEY,
                    RedisStreamConstants.CLICK_GROUP
            );
            log.debug("消费者组创建/确认成功，streamKey={}, group={}",
                    RedisStreamConstants.CLICK_STREAM_KEY, RedisStreamConstants.CLICK_GROUP);
        } catch (Exception e) {
            log.debug("消费者组可能已存在: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void consumeClickEvent() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(RedisStreamConstants.CLICK_GROUP,
                            RedisStreamConstants.CLICK_CONSUMER),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(RedisStreamConstants.CLICK_STREAM_KEY,
                            ReadOffset.lastConsumed())
            );
        } catch (Exception e) {
            log.error("读取消息失败", e);
            return;
        }
        if (records != null && !records.isEmpty()) {
            processBatch(records);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void reclaimPending() {
        PendingMessages pendingMessages;
        try {
            pendingMessages = stringRedisTemplate.opsForStream()
                    .pending(RedisStreamConstants.CLICK_STREAM_KEY,
                            RedisStreamConstants.CLICK_GROUP,
                            Range.unbounded(), 1000L);
        } catch (Exception e) {
            log.error("获取PEL消息失败", e);
            return;
        }
        if (pendingMessages.isEmpty()) {
            return;
        }

        List<RecordId> idsToClaim = new ArrayList<>();
        for (PendingMessage msg : pendingMessages) {
            if (msg.getElapsedTimeSinceLastDelivery().toMillis() >= PENDING_IDLE_MS) {
                idsToClaim.add(msg.getId());
            }
        }

        if (idsToClaim.isEmpty()) {
            return;
        }

        try {
            List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream()
                    .claim(RedisStreamConstants.CLICK_STREAM_KEY,
                            RedisStreamConstants.CLICK_GROUP,
                            RedisStreamConstants.CLICK_CONSUMER,
                            Duration.ofMillis(PENDING_IDLE_MS),
                            idsToClaim.toArray(new RecordId[0]));
            if (claimed != null && !claimed.isEmpty()) {
                log.info("认领了 {} 条超时消息", claimed.size());
                processBatch(claimed);
            }
        } catch (Exception e) {
            log.error("认领消息失败", e);
        }
    }

    /**
     * 批量处理点击事件：按 shortCode 聚合计数 → 批量更新数据库 → ACK 确认。
     */
    private void processBatch(List<MapRecord<String, Object, Object>> records) {
        Map<String, Integer> countMap = new HashMap<>();
        Map<String, List<String>> codeToRecordIds = records.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.getValue().get("shortCode"),
                        Collectors.mapping(r -> r.getId().getValue(), Collectors.toList())
                ));

        for (MapRecord<String, Object, Object> record : records) {
            String shortCode = (String) record.getValue().get("shortCode");
            if (shortCode != null) {
                countMap.merge(shortCode, 1, Integer::sum);
            }
        }

        log.info("处理 {} 条消息，涉及 {} 个短码", records.size(), countMap.size());

        List<String> successIds = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            String code = entry.getKey();
            int delta = entry.getValue();
            try {
                shortLinkService.updateClickCount(code, delta);
                List<String> ids = codeToRecordIds.get(code);
                if (ids != null) {
                    successIds.addAll(ids);
                }
            } catch (Exception e) {
                log.error("更新失败，shortCode={}, delta={}", code, delta, e);
            }
        }

        for (String id : successIds) {
            try {
                stringRedisTemplate.opsForStream().acknowledge(
                        RedisStreamConstants.CLICK_STREAM_KEY,
                        RedisStreamConstants.CLICK_GROUP,
                        id
                );
            } catch (Exception e) {
                log.error("ACK失败，id={}", id, e);
            }
        }
    }

    @Scheduled(fixedDelay = 1_800_000)
    public void trimStream() {
        try {
            Long len = stringRedisTemplate.opsForStream()
                    .size(RedisStreamConstants.CLICK_STREAM_KEY);
            if (len != null && len > TRIM_THRESHOLD) {
                stringRedisTemplate.opsForStream()
                        .trim(RedisStreamConstants.CLICK_STREAM_KEY, TRIM_RETAIN);
                log.info("Stream 清理完成，之前长度: {}", len);
            }
        } catch (IllegalStateException e) {
            log.debug("应用关闭中，跳过清理: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Stream 清理失败", e);
        }
    }
}