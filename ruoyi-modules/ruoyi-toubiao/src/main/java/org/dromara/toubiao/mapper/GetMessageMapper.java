package org.dromara.toubiao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.toubiao.domain.TenderProjectDetail;

import java.util.List;
@Mapper
public interface GetMessageMapper {

     int insertIntoTenderProjectList(@Param("tenderProjectDetailList") List<TenderProjectDetail> tenderProjectDetailList);

    List<TenderProjectDetail> selectMissingInfoUrlList();

    int updateTenderProjectDetail(TenderProjectDetail detail);

    /**
     * 统计未解析记录数
     */
    int countUnparsedRecords();
    /**
     * 统计未解析记录数(只成功版
     */
    int countUnparsedRecordsOnlyS();

    /**
     * 查询待解析记录（bak为null）
     */
    List<TenderProjectDetail> selectUnparsedListOnlyS(@Param("limit") int limit);

    /**
     * 更新解析状态（bak字段）
     */
    int updateParseStatus(@Param("infoid") String infoid, @Param("status") String status);

    /**
     * 根据infoid查询记录
     */
    TenderProjectDetail selectByInfoId(@Param("infoid") String infoid);

    /**
     * 根据状态统计记录数
     */
    int countByStatus(@Param("status") String status);

    /**
     * 重置失败记录状态（将失败状态重置为null）
     */
    int resetFailedStatus(@Param("infoid") String infoid);

    /**
     * 批量更新解析状态
     */
    int batchUpdateParseStatus(@Param("infoids") List<String> infoids, @Param("status") String status);

    /**
     * 查询成功记录
     */
    List<TenderProjectDetail> selectSuccessRecords(@Param("limit") int limit);

}
