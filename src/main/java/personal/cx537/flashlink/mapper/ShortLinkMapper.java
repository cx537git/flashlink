package personal.cx537.flashlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import personal.cx537.flashlink.entity.ShortLink;

/**
 * 短链接数据访问层，继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 *
 * @author Ethan Wu
 */
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {
}