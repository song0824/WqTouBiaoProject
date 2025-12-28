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
}
