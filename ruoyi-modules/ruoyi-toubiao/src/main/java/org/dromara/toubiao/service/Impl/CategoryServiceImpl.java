package org.dromara.toubiao.service.Impl;

import org.dromara.toubiao.domain.CategoryMessage;
import org.dromara.toubiao.domain.CategoryMessageVO;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.dromara.toubiao.mapper.TenderProjectDetailParsedMapper;
import org.dromara.toubiao.service.CategoryService;
import org.dromara.toubiao.service.CategoryUpdateService;
import org.dromara.toubiao.utils.AiCategory.CozeApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/21 17:38
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    CozeApiClient cozeApiClient;

    @Autowired
    TenderProjectDetailParsedMapper tenderProjectDetailParsedMapper;

    @Autowired
    CategoryUpdateService categoryUpdateService;

    @Override
    /**
     * 执行项目分类的方法
     * 该方法获取所有项目信息，并通过工作流对每个项目进行分类处理
     * 使用@Async注解使该方法异步执行，不会阻塞其他方法的执行
     */
    @Async
//    @Transactional
    public void Category() {
        // 获取所有项目分类信息
        List<CategoryMessage> categoryMessage = getCategoryMessage();
        // 遍历每个项目信息进行分类处理
        for (CategoryMessage message : categoryMessage) {
            try {
                // 调用工作流进行分类处理，传入项目ID、项目概述、项目名称和项目需求等信息
                CategoryMessageVO vo = cozeApiClient.classifyByWorkflow(
                    String.valueOf(message.getId()),      // 项目ID
                    message.getSectionProjectOverview(), // 项目概述
                    message.getProname(),               // 项目名称
                    message.getSectionProjectNeed()      // 项目需求
                );

                // 执行更新操作
                categoryUpdateService.updateCategoryInfo(message.getId(), vo);

            } catch (IOException e) {
                // 输出分类失败的错误信息
                System.err.println("项目ID: " + message.getId() + " 分类失败: " + e.getMessage());
                // 打印异常堆栈信息
                e.printStackTrace();
            }
        }
    }

    @Override
    public List<CategoryMessage> getCategoryMessage() {
        return tenderProjectDetailParsedMapper.selectCategoryMessage();
    }

}
