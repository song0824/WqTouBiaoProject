package org.dromara.toubiao.controller;



import cn.dev33.satoken.annotation.SaIgnore;
import org.dromara.toubiao.service.GetMessageService;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 功能：
 * 作者：张
 * 日期：2025/12/24 15:34
 */
@RestController
public class GetMessageController {

    @Autowired
    private GetMessageService getMessageService;

    @SaIgnore
    @GetMapping("/getData")
    public String getData() {
        return getMessageService.WriteToDataBase();
    }
}
