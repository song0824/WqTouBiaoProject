package org.dromara.toubiao.service.Impl;

import jakarta.annotation.Resource;
import org.dromara.toubiao.domain.CategoryMessageDTO;
import org.dromara.toubiao.domain.CategoryMessageVO;
import org.dromara.toubiao.mapper.TenderProjectCategoryMapper;
import org.dromara.toubiao.mapper.TenderProjectDetailParsedMapper;
import org.dromara.toubiao.service.CategoryUpdateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class CategoryUpdateServiceImpl implements CategoryUpdateService {

    @Resource
    private TenderProjectDetailParsedMapper tenderProjectDetailParsedMapper;

    @Resource
    private TenderProjectCategoryMapper tenderProjectCategoryMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCategoryInfo(Integer id, CategoryMessageVO vo) {
        tenderProjectDetailParsedMapper.updateAiCategoryInfo(
            id,
            vo.getIsAiClassified(),
            LocalDateTime.now(),
            vo.getCategoryCode()
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertAndUpdateCategoryInfo(List<CategoryMessageDTO> list) {
        //1更新tender_project_detail_parsed表的is_ai_classified字段为1
        tenderProjectDetailParsedMapper.updateAiCategory(list.get(0).getProjectId());
        //2插入tender_project_category表
        tenderProjectCategoryMapper.insertCategroyMessage(list);

    }
}
