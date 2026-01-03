package org.dromara.toubiao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.toubiao.domain.TenderProjectDetail;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;

import java.util.List;

/**
 * 招标项目解析详情 Mapper接口
 *
 * @author
 * @date 2025-12-30
 */
@Mapper
public interface TenderParsedMapper {

    /**
     * 插入解析记录
     *
     * @param parsed 解析详情对象
     * @return 影响行数
     */
    int insert(TenderProjectDetailParsed parsed);

    /**
     * 更新解析记录
     *
     * @param parsed 解析详情对象
     * @return 影响行数
     */
    int update(TenderProjectDetailParsed parsed);

    /**
     * 根据infoid查询解析记录
     *
     * @param infoid 项目唯一标识
     * @return 解析详情对象
     */
    TenderProjectDetailParsed selectByInfoId(@Param("infoid") String infoid);

    /**
     * 查询待解析列表（从主表查询有URL但未解析的记录）
     *
     * @param limit 查询数量限制
     * @return 待解析项目列表
     */
    List<TenderProjectDetail> selectUnparsedList(@Param("limit") int limit);

    /**
     * 查询解析失败且可重试的记录
     *
     * @param maxRetryCount 最大重试次数
     * @param limit 查询数量限制
     * @return 可重试的解析记录列表
     */
    List<TenderProjectDetailParsed> selectRetryableList(
        @Param("maxRetryCount") int maxRetryCount,
        @Param("limit") int limit
    );

    /**
     * 更新解析状态
     *
     * @param infoid 项目唯一标识
     * @param status 解析状态
     * @param errorMsg 错误信息
     * @return 影响行数
     */
    int updateParseStatus(
        @Param("infoid") String infoid,
        @Param("status") int status,
        @Param("errorMsg") String errorMsg
    );

    /**
     * 查询解析成功但未AI分类的记录（为AI预留）
     *
     * @param limit 查询数量限制
     * @return 待分类记录列表
     */
    List<TenderProjectDetailParsed> selectUnclassifiedList(@Param("limit") int limit);

    /**
     * 更新AI分类状态
     *
     * @param infoid 项目唯一标识
     * @param status AI分类状态（0-否 1-是）
     * @return 影响行数
     */
    int updateAIStatus(
        @Param("infoid") String infoid,
        @Param("status") int status
    );

    /**
     * 统计解析状态数量
     *
     * @param status 解析状态
     * @return 数量
     */
    int countByStatus(@Param("status") int status);
}
