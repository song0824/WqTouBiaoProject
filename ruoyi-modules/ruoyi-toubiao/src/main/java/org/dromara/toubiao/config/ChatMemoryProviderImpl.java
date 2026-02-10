package org.dromara.toubiao.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/4 21:40
 */
@Component("chatMemoryProviderImpl")
public class ChatMemoryProviderImpl implements ChatMemoryProvider {

    private final Map<String, ChatMemory> memoryMap = new ConcurrentHashMap<>();

    @Override
    public ChatMemory get(Object memoryId) {
        return memoryMap.computeIfAbsent(
            memoryId.toString(),
            id -> MessageWindowChatMemory.withMaxMessages(10)
        );
    }
}
