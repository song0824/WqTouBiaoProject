package org.dromara.toubiao.service.Impl;

import org.dromara.toubiao.service.CategoryService;
import org.dromara.toubiao.utils.AiCategory.CozeApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/21 17:38
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    CozeApiClient cozeApiClient;

    @Override
    public void Category() {


        String message = "项目名称：中药材年度框架协议采购，项目概述：为保障中药材原料质量稳定、供应及时，建立长期战略合作关系，现对多品类中药材进行年度框架协议采购，欢迎符合条件的优质供应商参与。";

        String  code = cozeApiClient.testCozeApiCall(message);

    }
}
