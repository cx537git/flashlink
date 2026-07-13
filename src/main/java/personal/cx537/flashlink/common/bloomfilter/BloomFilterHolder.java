package personal.cx537.flashlink.common.bloomfilter;

import com.google.common.hash.BloomFilter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 布隆过滤器双缓冲持有者。
 *
 * <h3>设计决策：双缓冲切换</h3>
 * <p>布隆过滤器不支持删除操作，当短链过期或禁用后无法从过滤器中移除，长期运行会导致
 * 误判率上升。采用双缓冲（current + next）机制，通过定时重建解决此问题：</p>
 * <ol>
 *   <li>{@link #startRebuild} 将新过滤器放入 next 槽位</li>
 *   <li>重建期间新生成的短码同时写入 current 和 next 两个过滤器</li>
 *   <li>{@link #finishRebuild} 用 next 原子替换 current</li>
 * </ol>
 * <p>整个过程无锁、无停顿，重建期间短链生成不受影响。</p>
 *
 * @author Ethan Wu
 */
public class BloomFilterHolder {

    private static final AtomicReference<BloomFilter<String>> currentRef = new AtomicReference<>();
    private static final AtomicReference<BloomFilter<String>> nextRef = new AtomicReference<>();

    public static void initFirstFilter(BloomFilter<String> filter) {
        currentRef.set(filter);
    }

    public static BloomFilter<String> getCurrent() {
        return currentRef.get();
    }

    public static BloomFilter<String> getNext() {
        return nextRef.get();
    }

    public static void startRebuild(BloomFilter<String> newFilter) {
        nextRef.set(newFilter);
    }

    public static void finishRebuild() {
        BloomFilter<String> next = nextRef.getAndSet(null);
        if (next != null) {
            currentRef.set(next);
        }
    }

    public static void add(String shortCode) {
        BloomFilter<String> current = currentRef.get();
        if (current != null) {
            current.put(shortCode);
        }
        BloomFilter<String> next = nextRef.get();
        if (next != null) {
            next.put(shortCode);
        }
    }
}