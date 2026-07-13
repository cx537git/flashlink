package personal.cx537.flashlink.common.exception;

/**
 * 短链不存在异常，HTTP 404 Not Found。
 *
 * @author Ethan Wu
 */
public class ShortLinkNotFoundException extends RuntimeException {
    public ShortLinkNotFoundException(String shortCode) {
        super("短链不存在：" + shortCode);
    }
}