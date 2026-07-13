package personal.cx537.flashlink.generator.impl;

import org.springframework.stereotype.Component;
import personal.cx537.flashlink.generator.ShortCodeGenerator;

/**
 * 基于 Snowflake ID + Base62 编码的短链码生成器。
 *
 * <h3>核心算法</h3>
 * <p>将 Snowflake 生成的 64 位长整型 ID 通过 <b>除基取余法</b> 转换为 62 进制字符串。
 * 字符表为 {@code [0-9A-Za-z]}，共 62 个字符，既保证 URL 安全，又使短码长度可控
 * （Snowflake ID 约 10^18 量级，Base62 编码后约 11 位）。</p>
 *
 * <h3>进制选择决策</h3>
 * <ul>
 *   <li>Base62 vs Base64：Base64 含 +/ 符号，在 URL 中需转义，Base62 天然 URL-safe</li>
 *   <li>Base62 vs 纯数字：相同 ID 下 Base62 比十进制短约 40%，用户体验更好</li>
 * </ul>
 *
 * @author Ethan Wu
 */
@Component
public class SnowflakeShortCodeGenerator implements ShortCodeGenerator {

    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @Override
    public String generateShortCode(long id) {
        return toBase62(id);
    }

    /**
     * 除基取余法实现 Base62 编码。
     *
     * <p>算法步骤：对 id 反复除以 62，每次取余数作为字符表索引，
     * 最后反转字符串得到最终编码。id=0 边界情况直接返回 "0"。</p>
     *
     * @param id Snowflake 生成的原始 ID
     * @return Base62 编码的短链码
     */
    private String toBase62(long id) {
        if (id == 0) {
            return "0";
        }

        StringBuilder result = new StringBuilder();

        while (id > 0) {
            int index = (int) (id % 62);
            result.append(BASE62_CHARS.charAt(index));
            id /= 62;
        }

        return result.reverse().toString();
    }
}