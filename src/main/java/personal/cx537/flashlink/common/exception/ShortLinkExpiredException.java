package personal.cx537.flashlink.common.exception;

/**
 * 短链已过期异常，HTTP 410 Gone。
 *
 * @author Ethan Wu
 */
public class ShortLinkExpiredException extends RuntimeException {
    public ShortLinkExpiredException(String shortCode) {
        super("短链已过期: " + shortCode);
    }
}