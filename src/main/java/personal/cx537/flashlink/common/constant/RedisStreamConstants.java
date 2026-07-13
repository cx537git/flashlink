package personal.cx537.flashlink.common.constant;

/**
 * Redis Stream 消息队列常量。
 *
 * <p>点击事件通过 Stream 异步传递，解耦短链解析与点击计数更新。
 * 消费者组模式保证消息至少被消费一次（At-Least-Once）。</p>
 *
 * @author Ethan Wu
 */
public class RedisStreamConstants {
    public static final String CLICK_STREAM_KEY = "stream:click";
    public static final String CLICK_GROUP = "click-group";
    public static final String CLICK_CONSUMER = "consumer-1";
}