package org.henan.toubiao.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.henan.toubiao.domain.HenanTradeAnno;

import java.util.List;

/**
 * 河南省公共资源交易公告 Mapper
 */
@Mapper
public interface HenanTradeAnnoMapper {

    @InterceptorIgnore(tenantLine = "true")
    int batchInsert(@Param("list") List<HenanTradeAnno> list);

    @InterceptorIgnore(tenantLine = "true")
    int countById(@Param("id") String id);
}
