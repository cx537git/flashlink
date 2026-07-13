package personal.cx537.flashlink.common.exception;

/**
 * 系统繁忙异常，HTTP 503 Service Unavailable。
 *
 * <p>触发场景：Guava Striped 细粒度锁获取超时（3秒），表示当前短码并发过高。</p>
 *
 * @author Ethan Wu
 */
public class SystemBusyException extends RuntimeException {
    public SystemBusyException(String message) {
        super(message);
    }
}