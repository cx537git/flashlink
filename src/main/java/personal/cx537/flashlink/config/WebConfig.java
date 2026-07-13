package personal.cx537.flashlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import personal.cx537.flashlink.common.interceptor.RateLimitInterceptor;

/**
 * Web MVC 配置，注册限流拦截器。
 *
 * <p>限流拦截器作用于 /api/short-link 和 /s/** 两个核心路径。</p>
 *
 * @author Ethan Wu
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor())
                .addPathPatterns("/s/**", "/api/short-link");
    }

    @Bean
    public RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor();
    }
}