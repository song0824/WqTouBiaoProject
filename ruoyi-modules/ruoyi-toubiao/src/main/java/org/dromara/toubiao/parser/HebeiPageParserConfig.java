package org.dromara.toubiao.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 河北页面解析器配置
 *
 * @author
 * @date 2025-12-30
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hebei.parser")
public class HebeiPageParserConfig {

    /**
     * HTTP请求超时时间（毫秒）
     */
    private Integer requestTimeout = 30000;

    /**
     * 解析重试次数
     */
    private Integer maxRetryCount = 3;

    /**
     * 批量解析数量
     */
    private Integer batchSize = 100;

    /**
     * 并发线程数
     */
    private Integer threadPoolSize = 10;

    /**
     * 请求间隔时间（毫秒）
     */
    private Integer requestInterval = 200;
}
