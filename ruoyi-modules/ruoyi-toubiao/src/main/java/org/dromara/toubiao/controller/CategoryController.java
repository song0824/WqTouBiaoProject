package org.dromara.toubiao.controller;

import org.dromara.toubiao.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public void test(){
        categoryService.Category();
    }

}
