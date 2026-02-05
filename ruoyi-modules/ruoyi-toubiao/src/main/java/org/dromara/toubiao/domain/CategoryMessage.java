
package org.dromara.toubiao.domain;

import lombok.Data;
import java.io.Serializable;

/**
 * 分类消息DTO
 * 用于封装招标项目的分类相关信息
 */
@Data
public class CategoryMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 项目名称
     */
    private String proname;

    /**
     * 采购需求
     */
    private String sectionProjectNeed;

    /**
     * 项目概况
     */
    private String sectionProjectOverview;
}
