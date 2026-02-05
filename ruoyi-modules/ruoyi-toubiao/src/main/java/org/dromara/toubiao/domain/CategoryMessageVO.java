
package org.dromara.toubiao.domain;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 分类消息VO
 * 用于展示招标项目的分类相关信息
 */
@Data
public class CategoryMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 是否AI分类
     */
    private Boolean isAiClassified;

    /**
     * AI分类时间
     */
    private Date aiClassifyTime;

    /**
     * 分类编码
     */
    private String categoryCode;
}
