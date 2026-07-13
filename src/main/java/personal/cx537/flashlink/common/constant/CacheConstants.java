package personal.cx537.flashlink.common.constant;

/**
 * Redis 缓存键常量与时间配置。
 *
 * <p>NOT_FOUND 和 EXPIRED 作为空值缓存哨兵，用于防止缓存穿透。
 * 过期时间使用随机偏移（3-6 分钟），避免缓存雪崩。</p>
 *
 * @author Ethan Wu
 */
public class CacheConstants {
    public static final String SHORT_LINK_PREFIX = "short_link:short_code:";
    public static final long DEFAULT_TTL_HOURS = 24;
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String EXPIRED = "EXPIRED";
}