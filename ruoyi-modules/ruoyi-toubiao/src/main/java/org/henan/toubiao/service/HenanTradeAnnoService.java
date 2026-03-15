package org.henan.toubiao.service;

/**
 * 河南省公共资源交易公告 - 爬取并入库
 */
public interface HenanTradeAnnoService {

    /**
     * 爬取当天采购公告并入库（仅当天 createTime 的数据，且填充 infoUrl）
     * @return 本次入库条数
     */
    int crawlTodayAndSave();
}
