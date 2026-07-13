package personal.cx537.flashlink.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.util.concurrent.Striped;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import personal.cx537.flashlink.common.bloomfilter.BloomFilterHolder;
import personal.cx537.flashlink.common.constant.CacheConstants;
import personal.cx537.flashlink.common.constant.RedisStreamConstants;
import personal.cx537.flashlink.common.exception.ShortLinkExpiredException;
import personal.cx537.flashlink.common.exception.ShortLinkNotFoundException;
import personal.cx537.flashlink.common.exception.SystemBusyException;
import personal.cx537.flashlink.entity.ShortLink;
import personal.cx537.flashlink.generator.ShortCodeGenerator;
import personal.cx537.flashlink.mapper.ShortLinkMapper;
import personal.cx537.flashlink.service.ShortLinkService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 短链接服务核心实现。
 *
 * <h3>短链解析的三级查找策略</h3>
 * <ol>
 *   <li><b>布隆过滤器预判</b> — 不存在则直接返回 404，防止缓存穿透</li>
 *   <li><b>Redis 缓存查询</b> — 命中则直接返回，同时异步发布点击事件</li>
 *   <li><b>数据库回源</b> — 缓存未命中时加分布式锁回源数据库，写入缓存后返回</li>
 * </ol>
 *
 * <h3>并发控制</h3>
 * <p>使用 Guava Striped（1024 个槽位）按 shortCode 做细粒度锁，同一短码
 * 串行回源数据库，不同短码并行处理。tryLock 超时时间 3 秒，超时抛 503。</p>
 *
 * <h3>缓存穿透防护</h3>
 * <p>不存在的短码缓存 NOT_FOUND 哨兵值（3-6 分钟随机 TTL），
 * 已过期的短码缓存 EXPIRED 哨兵值，均带随机过期时间防止雪崩。</p>
 *
 * @author Ethan Wu
 */
@Slf4j
@Service
public class ShortLinkServiceImpl implements ShortLinkService {
    @Autowired
    private ShortCodeGenerator shortCodeGenerator;
    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Striped<Lock> striped = Striped.lazyWeakLock(1024);


    @Override
    public String createShortLink(String originUrl, LocalDateTime expireTime) {
        long id = IdUtil.getSnowflakeNextId();
        String shortCode = shortCodeGenerator.generateShortCode(id);

        ShortLink shortLink = new ShortLink();
        shortLink.setId(id);
        shortLink.setShortCode(shortCode);
        shortLink.setOriginalUrl(originUrl);
        shortLink.setExpireTime(expireTime);

        log.info("短链生成成功：{}", shortLink);

        BloomFilterHolder.add(shortCode);
        shortLinkMapper.insert(shortLink);

        return shortCode;
    }

    @Override
    public String resolveShortLink(String shortCode) {
        if (!BloomFilterHolder.getCurrent().mightContain(shortCode)) {
            log.info("布隆过滤器判定短链不存在：{}", shortCode);
            throw new ShortLinkNotFoundException(shortCode);
        }

        String cacheUrl = getCacheUrl(shortCode);
        if (cacheUrl != null) {
            return cacheUrl;
        }

        Lock lock = striped.get(shortCode);

        try {
            if (lock.tryLock(3, TimeUnit.SECONDS)) {
                try {
                    cacheUrl = getCacheUrl(shortCode);
                    return Objects.requireNonNullElseGet(cacheUrl, () -> queryAndCache(shortCode));
                } finally {
                    lock.unlock();
                }
            } else {
                throw new SystemBusyException("系统繁忙，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SystemBusyException("获取锁被中断");
        }
    }

    private @NonNull String queryAndCache(String shortCode) {
        ShortLink shortLink = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getShortCode, shortCode)
                        .eq(ShortLink::getStatus, 1)
        );

        if (shortLink == null) {
            log.info("短链不存在：{}", shortCode);
            stringRedisTemplate.opsForValue().set(CacheConstants.SHORT_LINK_PREFIX + shortCode,
                    CacheConstants.NOT_FOUND,
                    Duration.ofMinutes(RandomUtil.randomInt(3, 6))
            );
            throw new ShortLinkNotFoundException(shortCode);
        }

        Duration duration;
        LocalDateTime expireTime = shortLink.getExpireTime();
        if (expireTime != null) {
            if (expireTime.isBefore(LocalDateTime.now())) {
                log.info("短链已过期：{}，过期时间：{}", shortCode, expireTime);
                stringRedisTemplate.opsForValue().set(CacheConstants.SHORT_LINK_PREFIX + shortCode,
                        CacheConstants.EXPIRED,
                        Duration.ofMinutes(RandomUtil.randomInt(3, 6))
                );
                throw new ShortLinkExpiredException(shortCode);
            }
            duration = Duration.between(LocalDateTime.now(), expireTime);
        } else {
            duration = Duration.ofHours(CacheConstants.DEFAULT_TTL_HOURS + RandomUtil.randomInt(0, 2));
        }

        String originalUrl = shortLink.getOriginalUrl();
        log.debug("从数据库中获取短链：{}", originalUrl);
        stringRedisTemplate.opsForValue().set(CacheConstants.SHORT_LINK_PREFIX + shortCode,
                originalUrl,
                duration
        );
        publishClickEvent(shortCode);
        return originalUrl;
    }

    private @Nullable String getCacheUrl(String shortCode) {
        String cacheUrl = stringRedisTemplate.opsForValue().get(CacheConstants.SHORT_LINK_PREFIX + shortCode);
        if (cacheUrl != null) {
            if (cacheUrl.equals(CacheConstants.NOT_FOUND)) {
                log.info("短链不存在：{}", shortCode);
                throw new ShortLinkNotFoundException(shortCode);
            }
            if (cacheUrl.equals(CacheConstants.EXPIRED)) {
                log.info("短链已过期：{}", shortCode);
                throw new ShortLinkExpiredException(shortCode);
            }
            log.debug("从 redis 缓存中获取短链：{}", cacheUrl);
            publishClickEvent(shortCode);
            return cacheUrl;
        } else {
            return null;
        }
    }

    private void publishClickEvent(String shortCode) {
        Map<String, String> message = new HashMap<>();
        message.put("shortCode", shortCode);
        stringRedisTemplate.opsForStream().add(StreamRecords.newRecord()
                .in(RedisStreamConstants.CLICK_STREAM_KEY)
                .ofMap(message)
        );
    }

    @Override
    public List<String> getAllShortCode() {
        List<ShortLink> shortLinks = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                .select(ShortLink::getShortCode)
                .eq(ShortLink::getStatus, 1)
                .and(w -> w.isNull(ShortLink::getExpireTime).or().gt(ShortLink::getExpireTime, LocalDateTime.now()))
        );
        return shortLinks.stream().map(ShortLink::getShortCode).toList();
    }

    @Override
    public void updateClickCount(String shortCode, Integer clickCount) {
        shortLinkMapper.update(new LambdaUpdateWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode)
                .setSql("click_count = click_count + {0}", clickCount)
        );
    }

    @Override
    public List<String> getShortCodesCreatedAfter(LocalDateTime startTime) {
        List<ShortLink> shortLinks = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                .select(ShortLink::getShortCode)
                .eq(ShortLink::getStatus, 1)
                .ge(ShortLink::getCreateTime, startTime)
        );

        return shortLinks.stream().map(ShortLink::getShortCode).toList();
    }
}