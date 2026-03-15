package org.henan.toubiao.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.henan.toubiao.client.HenanTradeAnnoClient;
import org.henan.toubiao.domain.HenanTradeAnno;
import org.henan.toubiao.mapper.HenanTradeAnnoMapper;
import org.henan.toubiao.service.HenanTradeAnnoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 河南省公共资源交易公告 - 爬取并入库实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HenanTradeAnnoServiceImpl implements HenanTradeAnnoService {

    private final HenanTradeAnnoClient henanTradeAnnoClient;
    private final HenanTradeAnnoMapper henanTradeAnnoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int crawlTodayAndSave() {
        List<HenanTradeAnno> list = henanTradeAnnoClient.fetchTodayTradeAnnoList();
        if (list == null || list.isEmpty()) {
            log.info("河南省公告：当天无新数据");
            return 0;
        }
        List<HenanTradeAnno> toInsert = new ArrayList<>();
        for (HenanTradeAnno item : list) {
            if (item.getId() != null && henanTradeAnnoMapper.countById(item.getId()) == 0) {
                toInsert.add(item);
            }
        }
        if (toInsert.isEmpty()) {
            log.info("河南省公告：当天数据均已存在，跳过入库");
            return 0;
        }
        int cnt = henanTradeAnnoMapper.batchInsert(toInsert);
        log.info("河南省公告：本次入库 {} 条", cnt);
        return cnt;
    }
}
