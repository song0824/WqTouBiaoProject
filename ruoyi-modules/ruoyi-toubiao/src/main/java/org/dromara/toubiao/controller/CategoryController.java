package org.dromara.toubiao.controller;

import org.dromara.toubiao.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    /**
     * 项目【完全启动成功后】才执行分类任务
     * 解决：启动过早、Bean未就绪问题
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() {
        System.out.println("✅ 项目启动完成，延迟5秒后开始执行AI分类任务...");
        try {
            // 延迟10秒执行（确保数据库、HTTP、配置全部就绪）
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("🚀 开始执行AI项目分类任务");
        categoryService.Category();
    }

}
