package org.dromara.toubiao.utils.AiCategory;

import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.dromara.toubiao.domain.CategoryMessageVO;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Coze API 客户端组件（流式调用）
 */
@Component
public class CozeApiClient {
    // API 基础配置
    private static final String CHAT_API_URL = "https://api.coze.cn/v3/chat";
    private static final String WORKFLOW_API_URL = "https://api.coze.cn/v1/workflow/stream_run";
    private static final String TOKEN = "pat_Qm71jDfphTDtWeKaMHvIBQ8cAHrN5lh28q1cjLIWuJjJmO99gkjGt80AWkAdHhIP";
    private static final String BOT_ID = "7597390389269512192";
    private static final String WORKFLOW_ID = "7597392590997356594";
    private static final String USER_ID = "123456789";

    // 初始化 OkHttp 客户端（单例模式，避免重复创建）
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // 流式响应需要较长的读取超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();


    /**
     * 调用Coze工作流进行分类
     * @param id 项目ID
     * @param message 项目信息
     * @param name 项目名称
     * @param needs 项目需求
     * @return CategoryMessageVO 包含分类结果
     * @throws IOException IO异常
     */
    public CategoryMessageVO classifyByWorkflow(String id, String message, String name, String needs) throws IOException {
        // 创建返回对象
        CategoryMessageVO vo = new CategoryMessageVO();
        vo.setId(Integer.parseInt(id));
        vo.setIsAiClassified(1);
//        vo.setAiClassifyTime(new Date());
        vo.setCategoryCode("99");  //初始默认字符99

        // 1. 构建请求体 JSON 数据
        JSONObject requestBody = new JSONObject();
        requestBody.put("workflow_id", WORKFLOW_ID);

        // 构建参数
        JSONObject parameters = new JSONObject();
        parameters.put("id", id);
        parameters.put("message", message);
        parameters.put("name", name);
        parameters.put("needs", needs);

        requestBody.put("parameters", parameters);

        // 2. 构建请求
        Request request = new Request.Builder()
            .url(WORKFLOW_API_URL)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody.toJSONString()))
            .addHeader("Authorization", "Bearer " + TOKEN)
            .addHeader("Content-Type", "application/json")
            .build();

        // 记录请求发送时间
        long startTime = System.currentTimeMillis();

        // 3. 发送请求并处理流式响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code() + " " + response.message());
            }

            // 获取响应体的流式输入
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                System.out.println("响应体为空");
                return vo;
            }

            // 逐行读取流式响应
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 过滤空行和注释行
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }

                    // 分割 SSE 行：格式为 "字段名: 内容"
                    String[] parts = line.split(":", 2);
                    if (parts.length < 2) {
                        continue; // 无效的 SSE 行，跳过
                    }

                    String field = parts[0].trim(); // 字段名
                    String fieldContent = parts[1].trim(); // 字段内容

                    // 处理 data 字段（核心 JSON 数据）
                    if ("data".equals(field)) {
                        try {
                            JSONObject dataObj = JSONObject.parseObject(fieldContent);
                            String contentStr = dataObj.getString("content");

                            if (contentStr != null) {
                                JSONObject contentObj = JSONObject.parseObject(contentStr);
                                // 获取id和output
                                String contentId = contentObj.getString("id");
                                String output = contentObj.getString("output");

                                // 如果id匹配，则设置categoryCode
                                if (id.equals(contentId) && output != null && !"error".equals(output)) {
                                    // 只有当output是单个字符数字时才进行赋值
                                    if (output.matches("^\\d+$") && !output.contains("、") && !output.matches(".*[\\u4e00-\\u9fa5].*")) { // 匹配连续数字，不包含分隔符和中文
                                        vo.setCategoryCode(output);
                                        System.out.println("项目ID: " + contentId + ", 分类编码: " + output);
                                    } else {
                                        System.out.println("项目ID: " + contentId + ", 无效的分类编码: " + output + "，不进行赋值,默认99");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 静默处理异常，不打印错误信息
                        }
                    }
                }
            }
        }

        // 记录结束时间并计算总耗时
        long endTime = System.currentTimeMillis();
        double totalTimeSeconds = (endTime - startTime) / 1000.0;

        System.out.println("分类完成，项目ID: " + id +
                         "，分类编码: " + vo.getCategoryCode() +
                         "，总分类耗时: " + String.format("%.3f", totalTimeSeconds) + "s");

        return vo;
    }

    /**
     * 调用 Coze 流式 API 接口
     * @param content 用户提问内容
     * @return 整合后的完整回复内容
     * @throws IOException IO异常
     */
    public String callCozeApi(String content) throws IOException {
        // 拼接完整回复的字符串
        StringBuilder fullReply = new StringBuilder();

        // 1. 构建请求体 JSON 数据
        JSONObject requestBody = new JSONObject();
        requestBody.put("bot_id", BOT_ID);
        requestBody.put("user_id", USER_ID);
        requestBody.put("stream", true); // 开启流式返回

        // 构建 additional_messages 数组
        JSONObject message = new JSONObject();
        message.put("content_type", "text");
        message.put("role", "user");
        message.put("type", "question");
        message.put("content", content);

        requestBody.put("additional_messages", new JSONObject[]{message});
        requestBody.put("parameters", new JSONObject());

        // 2. 构建请求
        Request request = new Request.Builder()
            .url(CHAT_API_URL)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody.toJSONString()))
            .addHeader("Authorization", "Bearer " + TOKEN)
            .addHeader("Content-Type", "application/json")
            .build();

        // 3. 发送请求并处理流式响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code() + " " + response.message());
            }

            // 获取响应体的流式输入
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                System.out.println("响应体为空");
                return fullReply.toString();
            }

            // 逐行读取流式响应（标准 SSE 格式解析）
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                String line;
                String currentEvent = ""; // 记录当前 SSE 事件类型
                while ((line = reader.readLine()) != null) {
                    // 过滤空行和注释行（SSE 协议规范）
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }

                    // 分割 SSE 行：格式为 "字段名: 内容" 或 "字段名:内容"，只分割一次避免内容含冒号
                    String[] parts = line.split(":", 2);
                    if (parts.length < 2) {
                        continue; // 无效的 SSE 行，跳过
                    }

                    String field = parts[0].trim(); // 字段名（event/data/id 等）
                    String fieldContent = parts[1].trim(); // 字段内容

                    // 处理 event 字段（记录事件类型）
                    if ("event".equals(field)) {
                        currentEvent = fieldContent;
//                        System.out.println("当前SSE事件类型: " + currentEvent);
                    }

                    // 处理 data 字段（核心 JSON 数据）
                    if ("data".equals(field)) {
                        // 处理结束标识（兼容带双引号和不带双引号的[DONE]）
                        if (fieldContent.equals("[DONE]") || fieldContent.equals("\"[DONE]\"")) {
//                            System.out.println("流式响应结束");
                            break;
                        }

                        // 解析 JSON 数据并提取有效内容
                        try {
                            JSONObject dataObj = JSONObject.parseObject(fieldContent);
//                            System.out.println("收到流式数据: " + dataObj.toJSONString());

                            // 只提取有效事件类型的 content 内容
                            String role = dataObj.getString("role");
                            String type = dataObj.getString("type");
                            String contentVal = dataObj.getString("content");

                            // 筛选有效内容：assistant 角色 + answer 类型的事件（delta/completed 都包含有效内容）
                            if ("assistant".equals(role) && "answer".equals(type) && contentVal != null) {
                                // 避免重复拼接（completed 事件会返回完整内容，优先用这个）
                                if ("conversation.message.completed".equals(currentEvent)) {
                                    fullReply.setLength(0); // 清空之前的片段，直接用完整内容
                                    fullReply.append(contentVal);
                                } else if ("conversation.message.delta".equals(currentEvent)) {
                                    // delta 事件是片段，逐段拼接
                                    fullReply.append(contentVal);
                                }
//                                System.out.println("本次拼接内容: " + contentVal + " | 当前完整内容: " + fullReply);
                            }
                        } catch (Exception e) {
                            System.err.println("解析JSON失败: " + fieldContent);
                            // 仅打印异常，不中断流程
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return fullReply.toString();
    }
}
