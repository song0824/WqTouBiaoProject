package org.henan.toubiao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 河南省公共资源交易公告实体
 * 对应表：henan_trade_anno
 */
@Data
@TableName("henan_trade_anno")
public class HenanTradeAnno implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long dbId;

    /** 公告主键ID（接口返回，用于拼详情链接） */
    private String id;

    /** 公告类型 */
    @JsonProperty("annoType")
    private String annoType;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 交易地区 */
    @JsonProperty("tradeRegion")
    private String tradeRegion;

    /** 交易地区编码 */
    @JsonProperty("tradeRegionCode")
    private String tradeRegionCode;

    /** 项目名称 */
    @JsonProperty("projectName")
    private String projectName;

    /** 项目类型 */
    @JsonProperty("projectType")
    private String projectType;

    /** 项目编号 */
    @JsonProperty("projectCode")
    private String projectCode;

    /** 创建时间（公告日期） */
    @JsonProperty("createTime")
    private String createTime;

    /** 原文详情页完整URL */
    private String infoUrl;
}
