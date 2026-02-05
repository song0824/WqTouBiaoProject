package org.dromara.toubiao.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.dromara.toubiao.domain.CategoryMessage;
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
    @InterceptorIgnore(tenantLine = "true")  // 忽略多租户
    List<TenderProjectDetailParsed> selectAll();

    /**
     * 分页查询
     * @param page 分页参数
     * @param position 职位名称，可为空
     * @param title 标题，可为空
     * @return 分页结果
     */
    @InterceptorIgnore(tenantLine = "true")  // 忽略多租户
    IPage<TenderProjectDetailParsed> selectPageList(IPage<TenderProjectDetailParsed> page, 
                                                     @Param("position") String position, 
                                                     @Param("title") String title);

    /**
     * 根据ID查询
     * @param id 主键ID
     * @return 解析详情
     */
    @InterceptorIgnore(tenantLine = "true")  // 忽略多租户
    TenderProjectDetailParsed selectById(@Param("id") Integer id);

    /**
     * 查询分类信息
     * @return 分类信息列表
     */
    @InterceptorIgnore(tenantLine = "true")  // 忽略多租户,否则数据库表要加一个tender_id字段，这是mybatisplus的代理，在MybatisPlusConfig中配置
    List<CategoryMessage> selectCategoryMessage();

}
