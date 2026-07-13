package personal.cx537.flashlink.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 短链接实体，对应数据库 flash_link 表。
 *
 * <p>使用 MyBatis-Plus 的 ASSIGN_ID 策略自动生成 Snowflake 分布式 ID，
 * 逻辑删除标记 is_deleted 配合 {@link TableLogic} 实现软删除。
 *
 * @author Ethan Wu
 */
@Data
@Schema(description = "短链接实体")
public class ShortLink {

    @Schema(description = "主键ID（Snowflake算法生成）")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "短链码（Base62编码）")
    private String shortCode;

    @Schema(description = "原始长链接")
    private String originalUrl;

    @Schema(description = "点击次数")
    private Integer clickCount;

    /** 状态：0-禁用，1-启用 */
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "过期时间，null表示永不过期")
    private LocalDateTime expireTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @Schema(description = "逻辑删除标记：0-未删除，1-已删除")
    @TableLogic
    private Integer isDeleted;
}