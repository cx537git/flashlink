package personal.cx537.flashlink.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;

/**
 * 短链生成请求参数 DTO。
 *
 * <p>包含原始链接校验（非空、URL格式、http/https 协议限制）和过期时间校验（必须为未来时间）。
 *
 * @author Ethan Wu
 */
@Data
@Schema(description = "短链生成请求参数")
public class GenerateConditionDTO {

    @Schema(description = "原始长链接，必须以 http:// 或 https:// 开头", example = "https://www.example.com/very/long/url")
    @NotBlank(message = "原始链接不能为空")
    @URL(message = "原始链接格式不正确")
    @Pattern(
            regexp = "^https?://.*",
            message = "原始链接必须以 http:// 或 https:// 开头"
    )
    private String originalUrl;

    @Schema(description = "过期时间（可选），不填则永不过期", example = "2026-12-31T23:59:59")
    @Future(message = "过期时间必须是未来时间")
    private LocalDateTime expireTime;
}