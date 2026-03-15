package org.hebei.toubiao.service;

import org.hebei.toubiao.domain.CategoryMessage;

import java.util.List;

public interface CategoryService {

    void Category();

    /**
     * 获取招标项目分类信息
     * @return 分类信息列表
     */
    List<CategoryMessage> getCategoryMessage();

}
