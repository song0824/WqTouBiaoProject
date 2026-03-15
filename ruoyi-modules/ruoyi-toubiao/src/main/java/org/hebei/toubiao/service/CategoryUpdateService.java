package org.hebei.toubiao.service;

import org.hebei.toubiao.domain.CategoryMessageVO;

public interface CategoryUpdateService {

    void updateCategoryInfo(Integer id, CategoryMessageVO vo);
}
