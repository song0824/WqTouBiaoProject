package org.dromara.toubiao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招标项目分类实体类
 * 对应数据库表：tender_project_category
 *
 * @author
 * @date 2026-03-12
 */
@Data
@TableName("tender_project_category")
public class CategoryMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id字段
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 项目id
     */
    private String projectId;

    /**
     * 一级分类code
     */
    private String codeLevel1;

    /**
     * 一级名称
     */
    private String nameLevel1;

    /**
     * 二级分类code
     */
    private String codeLevel2;

    /**
     * 二级名称
     */
    private String nameLevel2;

    /**
     * 三级分类code
     */
    private String codeLevel3;

    /**
     * 三级名称
     */
    private String nameLevel3;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 分类是否正常，1正常，0不正常
     */
    private String isClassifyed;
}
