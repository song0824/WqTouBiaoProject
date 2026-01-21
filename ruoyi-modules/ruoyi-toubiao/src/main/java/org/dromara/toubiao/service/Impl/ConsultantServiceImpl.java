package org.dromara.toubiao.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.toubiao.domain.AiOpsResponse;
import org.dromara.toubiao.service.ConsultantAiService;
import org.dromara.toubiao.service.ConsultantService;
import org.dromara.toubiao.utils.AiChat.PromptBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/4 20:54
 */


@Service
public class ConsultantServiceImpl implements ConsultantService {

    private final ConsultantAiService aiService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ConsultantServiceImpl(ConsultantAiService aiService, PromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public AiOpsResponse chat(String userId, String userMessage) {

        String prompt = promptBuilder.build(userMessage);
        String raw = aiService.chat(userId, prompt);

        try {
            return objectMapper.readValue(raw, AiOpsResponse.class);
        } catch (Exception e) {
            return fallbackResponse(raw);
        }
    }

    private AiOpsResponse fallbackResponse(String raw) {
        AiOpsResponse res = new AiOpsResponse();
        res.setStatus("NOT_SURE");
        res.setSummary("模型输出格式异常");
        res.setAnalysis("模型未按约定 JSON 格式输出");
        res.setSolution(List.of());
        res.setWarnings(List.of("原始输出：" + raw));
        return res;
    }
}
