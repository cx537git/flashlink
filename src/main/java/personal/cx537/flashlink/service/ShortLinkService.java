package personal.cx537.flashlink.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短链接服务接口。
 *
 * <p>定义短链接的核心业务操作：创建、解析、全量查询、点击计数更新、增量查询。
 *
 * @author Ethan Wu
 */
public interface ShortLinkService {

    /**
     * 创建短链接。
     *
     * @param originUrl  原始长链接
     * @param expireTime 过期时间，null 表示永不过期
     * @return 生成的短链码
     */
    String createShortLink(String originUrl, LocalDateTime expireTime);

    /**
     * 解析短链接，获取原始长链接。
     *
     * <p>采用三级查找策略：布隆过滤器 → Redis 缓存 → 数据库，
     * 配合 Guava Striped 细粒度锁和空值缓存防止缓存穿透。
     *
     * @param shortLink 短链码
     * @return 原始长链接
     */
    String resolveShortLink(String shortLink);

    /**
     * 获取所有有效短链码（启用且未过期），用于布隆过滤器全量初始化。
     *
     * @return 有效短链码列表
     */
    List<String> getAllShortCode();

    /**
     * 批量更新点击计数，使用 SQL 原子递增。
     *
     * @param shortCode  短链码
     * @param clickCount 本次点击增量
     */
    void updateClickCount(String shortCode, Integer clickCount);

    /**
     * 查询指定时间后创建的短链码，用于布隆过滤器增量补偿。
     *
     * @param startTime 起始时间
     * @return 新增短链码列表
     */
    List<String> getShortCodesCreatedAfter(LocalDateTime startTime);
}