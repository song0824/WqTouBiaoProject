package org.dromara.toubiao.controller;

import org.dromara.toubiao.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.PostConstruct;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/21 17:36
 */

@RestController
@RequestMapping("/api")
public class CategoryController {


    @Autowired
    CategoryService categoryService;

    @GetMapping("/test")
    @Scheduled(cron = "0 0 18 * * ?") // 每天下午6点执行
    public void test(){
        categoryService.Category();
    }

    @PostConstruct
    public void init() {
        // 启动时立即执行一次分类任务
        System.out.println("启动时自动执行分类任务，已开始执行");
        categoryService.Category();
    }

}
