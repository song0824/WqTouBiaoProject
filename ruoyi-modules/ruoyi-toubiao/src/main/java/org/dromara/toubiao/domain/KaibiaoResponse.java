package org.dromara.toubiao.domain;

/**
 * 功能：
 * 作者：张
 * 日期：2025/12/24 19:36
 */
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
@Data
@Component
public class KaibiaoResponse {
    /** 总行数 */
    @JsonProperty("RowCount")
    private Integer rowCount;

    /** 项目列表 */
    @JsonProperty("Table")
    private List<TenderProjectDetail> table;
}
