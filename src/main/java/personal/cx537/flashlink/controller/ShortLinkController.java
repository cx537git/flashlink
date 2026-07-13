package personal.cx537.flashlink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import personal.cx537.flashlink.dto.GenerateConditionDTO;
import personal.cx537.flashlink.dto.Result;
import personal.cx537.flashlink.service.ShortLinkService;

import java.io.IOException;

/**
 * 短链接核心控制器。
 *
 * <p>提供两个核心 API：
 * <ul>
 *   <li><b>POST /api/short-link</b> — 生成短链接，受限流保护（10 QPS）</li>
 *   <li><b>GET /s/{shortCode}</b> — 解析短链接并 302 重定向，受限流保护（100 QPS）</li>
 * </ul>
 *
 * @author Ethan Wu
 */
@Slf4j
@RestController
@Tag(name = "短链接服务", description = "短链接生成与跳转相关接口")
public class ShortLinkController {

    @Autowired
    private ShortLinkService shortLinkService;

    /**
     * 生成短链接。
     *
     * <p>接收原始长链接和可选的过期时间，通过 Snowflake + Base62 算法生成唯一短码，
     * 写入数据库并同步至布隆过滤器。
     *
     * @param generateConditionDTO 包含原始链接和过期时间的请求体
     * @return 生成的短链码
     */
    @Operation(summary = "生成短链接", description = "将长链接转换为短链码，可选设置过期时间")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "短链生成成功，返回短链码"),
            @ApiResponse(responseCode = "400", description = "参数校验失败"),
            @ApiResponse(responseCode = "429", description = "请求过于频繁，触发限流")
    })
    @PostMapping("/api/short-link")
    public Result<String> generateShortLink(
            @Parameter(description = "原始链接与过期时间", required = true)
            @Valid @RequestBody GenerateConditionDTO generateConditionDTO) {
        String shortCode = shortLinkService.createShortLink(
                generateConditionDTO.getOriginalUrl(), generateConditionDTO.getExpireTime());

        log.info("生成短链码为：{}", shortCode);
        return Result.success(shortCode);
    }

    /**
     * 短链接跳转。
     *
     * <p>解析短链码，查询缓存或数据库获取原始链接，执行 302 临时重定向。
     * 解析过程包含布隆过滤器预判 → Redis 缓存查询 → 数据库回源的三级查找策略，
     * 并发控制使用 Guava Striped 细粒度锁。
     *
     * @param shortCode 短链码
     * @param response  HTTP 响应对象，用于 302 重定向
     */
    @Operation(summary = "短链接跳转", description = "通过短链码获取原始链接并302重定向")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "重定向至原始链接"),
            @ApiResponse(responseCode = "404", description = "短链不存在"),
            @ApiResponse(responseCode = "410", description = "短链已过期"),
            @ApiResponse(responseCode = "503", description = "系统繁忙，获取锁超时")
    })
    @GetMapping("/s/{shortCode}")
    public void getShortLink(
            @Parameter(description = "短链码", required = true, example = "aB3xK9")
            @PathVariable String shortCode,
            HttpServletResponse response) throws IOException {
        String originalUrl = shortLinkService.resolveShortLink(shortCode);

        log.info("临时重定向至：originalUrl：{}", originalUrl);
        response.sendRedirect(originalUrl);
    }
}