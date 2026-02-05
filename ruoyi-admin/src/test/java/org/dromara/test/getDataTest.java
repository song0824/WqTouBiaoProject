package org.dromara.test;


import org.dromara.DromaraApplication;
import org.dromara.toubiao.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = DromaraApplication.class)
@DisplayName("测试从数据库中获取项目信息")
public class getDataTest {

    @Autowired
    private CategoryService categoryService;

    @Test
    void test(){
        System.out.println(categoryService.getCategoryMessage());
    }
}
