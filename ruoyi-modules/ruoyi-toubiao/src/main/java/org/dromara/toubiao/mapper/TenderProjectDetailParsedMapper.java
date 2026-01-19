package org.dromara.toubiao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 招标项目解析详情 Mapper
 */
@Repository
public interface TenderProjectDetailParsedMapper extends BaseMapper<TenderProjectDetailParsed> {

    /**
     * 查询所有数据
     * @return 所有解析详情列表
     */
    List<TenderProjectDetailParsed> selectAll();

    /**
     * 分页查询
     * @param page 分页参数
     * @return 分页结果
     */
    IPage<TenderProjectDetailParsed> selectPageList(IPage<TenderProjectDetailParsed> page);

    /**
     * 根据ID查询
     * @param id 主键ID
     * @return 解析详情
     */
    TenderProjectDetailParsed selectById(@Param("id") Integer id);


}
