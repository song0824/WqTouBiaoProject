package org.dromara.toubiao.domain;

/**
 * 功能：
 * 作者：张
 * 日期：2025/12/24 18:46
 */


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 招标项目详情实体类
 * 对应数据库表：tender_project_detail
 */
@Data
@Component
@TableName("tender_project_detail")
public class TenderProjectDetail {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO) // MyBatis-Plus主键自增
    private Integer id;

    /**
     * 项目所属地区
     */
    private String area;

    /**
     * 备用字段
     */
    private String bak;

    /**
     * 开标场地
     */
    private String changdi;

    /**
     * 项目唯一标识ID
     */
    @JsonProperty("infoid")
    private String infoid;

    /**
     * 开标日期时间
     */
    @JsonProperty("kaibiaodate")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")  // 指定日期格式
    private LocalDateTime kaibiaodate;

    /**
     * 项目名称
     */
    @JsonProperty("proname")
    private String proname;

    /**
     * 项目编号
     */
    @JsonProperty("prono")
    private String prono;

    /**
     * 招标项目详情URL
     */
    @JsonProperty("info_url")
    private String infoUrl;
}
