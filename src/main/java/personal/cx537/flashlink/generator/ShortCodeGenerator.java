package personal.cx537.flashlink.generator;

/**
 * 短链码生成器策略接口。
 *
 * <p>将 Snowflake 生成的分布式唯一 ID 编码为短字符串，供外部访问使用。
 * 实现类需保证编码结果唯一且长度可控。</p>
 *
 * @author Ethan Wu
 */
public interface ShortCodeGenerator {

    /**
     * 将 Snowflake ID 编码为短链码。
     *
     * @param id Snowflake 分布式 ID
     * @return 短链码字符串
     */
    String generateShortCode(long id);
}