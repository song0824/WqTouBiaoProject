package org.dromara.toubiao.service;

import org.dromara.toubiao.domain.CategoryMessageDTO;
import org.dromara.toubiao.domain.CategoryMessageVO;

import java.util.List;

public interface CategoryUpdateService {

    void updateCategoryInfo(Integer id, CategoryMessageVO vo);

    void insertAndUpdateCategoryInfo(List<CategoryMessageDTO> list);
}
