package org.henan.toubiao.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.henan.toubiao.service.HenanTradeAnnoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 河南省公共资源交易公告 - 爬取触发接口
 */
@RestController
@RequestMapping("/henan/tradeAnno")
public class HenanTradeAnnoController {

    @Autowired
    private HenanTradeAnnoService henanTradeAnnoService;

    /**
     * 爬取当天采购公告并入库（仅当天数据，含原文详情 infoUrl）
     * GET /henan/tradeAnno/crawlToday
     */
    @SaIgnore
    @GetMapping("/crawlToday")
    public Map<String, Object> crawlToday() {
        int count = henanTradeAnnoService.crawlTodayAndSave();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "success");
        result.put("inserted", count);
        return result;
    }
}
