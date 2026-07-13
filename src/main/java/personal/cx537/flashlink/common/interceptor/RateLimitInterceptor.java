package personal.cx537.flashlink.common.interceptor;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 基于 Guava RateLimiter 的接口限流拦截器。
 *
 * <h3>限流策略</h3>
 * <ul>
 *   <li><b>POST /api/short-link</b> — 生成短链：10 QPS（写操作，成本高）</li>
 *   <li><b>GET /s/{shortCode}</b> — 短链跳转：100 QPS（读操作，核心链路）</li>
 * </ul>
 * <p>超限直接返回 HTTP 429 Too Many Requests，不阻塞等待。</p>
 *
 * @author Ethan Wu
 */
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter createRateLimiter = RateLimiter.create(10.0);
    private final RateLimiter redirectRateLimiter = RateLimiter.create(100.0);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/api/short-link")) {
            if (!createRateLimiter.tryAcquire()) {
                log.warn("生成短链限流触发：IP:{}", request.getRemoteAddr());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"生成短链过于频繁，请稍后重试\"}");
                response.flushBuffer();
                return false;
            }
        } else if (uri.startsWith("/s/")) {
            if (!redirectRateLimiter.tryAcquire()) {
                log.warn("短链跳转限流触发，IP:{}", request.getRemoteAddr());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"访问过于频繁，请稍后重试\"}");
                response.flushBuffer();
                return false;
            }
        }

        return true;
    }
}