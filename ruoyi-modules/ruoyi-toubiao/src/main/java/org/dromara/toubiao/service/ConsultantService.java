package org.dromara.toubiao.service;

import org.dromara.toubiao.domain.AiOpsResponse;

public interface ConsultantService {

    /**
     * 模型对话
     * @param userId 用户ID
     * @param userMessage 用户问题
     * @return 模型回答
     */
    AiOpsResponse chat(String userId, String userMessage);
}
