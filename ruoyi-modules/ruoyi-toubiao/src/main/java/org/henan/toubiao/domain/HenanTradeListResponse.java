package org.henan.toubiao.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 河南省交易公告列表接口响应
 * 兼容 data / records 等常见包装字段
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HenanTradeListResponse {

    /** 常见：data 列表 */
    private List<HenanTradeAnnoItem> data;
    /** 常见：records 列表 */
    private List<HenanTradeAnnoItem> records;
    /** 部分接口直接返回 list */
    private List<HenanTradeAnnoItem> list;
}
