package org.dromara.toubiao.utils.AiChat;

import org.springframework.stereotype.Component;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/4 21:04
 */
//        规则说明：
//            - 如果问题不属于网络运维领域，status=OUT_OF_SCOPE
//            - 如果信息不足，status=NOT_SURE
//            - solution 至少 1 步
//            - command 不是命令时填写空字符串

@Component
public class PromptBuilder {

    public String build(String userMessage) {
        return """

        【强制要求】
        - 你只能输出合法 JSON
        - 禁止输出 Markdown
        - 禁止输出任何解释性文字
        - 禁止输出 ```json ``` 包裹
        - 必须严格符合以下 JSON Schema

        JSON Schema:
        {
          "status": "OK | NOT_SURE | OUT_OF_SCOPE",
          "summary": "string",
          "analysis": "string",
          "solution": [
            {
              "step": number,
              "action": "string",
              "command": "string | empty"
            }
          ],
          "warnings": ["string"]
        }



        用户问题：
        %s
        """.formatted(userMessage);
    }
}
