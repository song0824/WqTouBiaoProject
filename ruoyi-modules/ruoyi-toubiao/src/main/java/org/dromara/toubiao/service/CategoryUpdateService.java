package org.dromara.toubiao.service;

import org.dromara.toubiao.domain.CategoryMessageVO;

public interface CategoryUpdateService {

    void updateCategoryInfo(Integer id, CategoryMessageVO vo);
}
