package personal.cx537.flashlink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应结果封装。
 *
 * <p>所有 API 返回值均通过此类包装，包含状态码、消息和泛型数据体。
 * 提供静态工厂方法快速构建成功/失败响应。
 *
 * @param <T> 响应数据类型
 * @author Ethan Wu
 */
@Data
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> {

    @Schema(description = "HTTP 状态码", example = "200")
    private int code;

    @Schema(description = "响应消息", example = "success")
    private String message;

    @Schema(description = "响应数据")
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}