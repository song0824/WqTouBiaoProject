package org.henan.toubiao.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口返回的单条公告（无 infoUrl）
 */
@Data
public class HenanTradeAnnoItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    @JsonProperty("annoType")
    private String annoType;
    private String title;
    private String content;
    @JsonProperty("tradeRegion")
    private String tradeRegion;
    @JsonProperty("tradeRegionCode")
    private String tradeRegionCode;
    @JsonProperty("projectName")
    private String projectName;
    @JsonProperty("projectType")
    private String projectType;
    @JsonProperty("projectCode")
    private String projectCode;
    @JsonProperty("createTime")
    private String createTime;
}
