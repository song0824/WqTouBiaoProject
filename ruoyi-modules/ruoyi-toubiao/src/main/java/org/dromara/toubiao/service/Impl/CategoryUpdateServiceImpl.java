package org.dromara.toubiao.service.Impl;

import jakarta.annotation.Resource;
import org.dromara.toubiao.domain.CategoryMessageVO;
import org.dromara.toubiao.mapper.TenderProjectDetailParsedMapper;
import org.dromara.toubiao.service.CategoryUpdateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class CategoryUpdateServiceImpl implements CategoryUpdateService {

    @Resource
    private TenderProjectDetailParsedMapper tenderProjectDetailParsedMapper;

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
}
