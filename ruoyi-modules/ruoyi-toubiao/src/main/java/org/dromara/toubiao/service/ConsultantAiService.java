package org.dromara.toubiao.service;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;
import dev.langchain4j.service.spring.AiService;
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    chatMemoryProvider  = "chatMemoryProviderImpl"
)
public interface ConsultantAiService {

    // 普通单轮对话
    @SystemMessage("""
           你是一名分类师，可以给用户提供以下功能：
           1.根据用户输入的项目概述，对项目进行分类，识别出该项目属于哪一类。
        """)
    String chat(@MemoryId String userId, @UserMessage String message);

    // 流式对话
    Flux<String> chatStream(@UserMessage String message);

    // 模块化回答优化
    String optimize(@UserMessage String prompt);


}
