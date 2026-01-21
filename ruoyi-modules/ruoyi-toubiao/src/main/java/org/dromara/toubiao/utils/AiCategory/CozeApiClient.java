package org.dromara.toubiao.utils.AiCategory;

import okhttp3.*;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


@Component
public class CozeApiClient {
    // API 基础配置
    private static final String API_URL = "https://api.coze.cn/v3/chat";
    private static final String TOKEN = "pat_Qm71jDfphTDtWeKaMHvIBQ8cAHrN5lh28q1cjLIWuJjJmO99gkjGt80AWkAdHhIP";
    private static final String BOT_ID = "7597390389269512192";
    private static final String USER_ID = "123456789";

    // 初始化 OkHttp 客户端
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // 流式响应需要较长的读取超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public static void main(String[] args) {
        callCozeApi();
    }

    /**
     * 调用 Coze 流式 API 接口
     */
    public static void callCozeApi() {
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
        message.put("content", "项目名称：中药材年度框架协议采购，项目概述：为保障中药材原料质量稳定、供应及时，建立长期战略合作关系，现对多品类中药材进行年度框架协议采购，欢迎符合条件的优质供应商参与。");

        requestBody.put("additional_messages", new JSONObject[]{message});
        requestBody.put("parameters", new JSONObject());

        // 2. 构建请求
        Request request = new Request.Builder()
            .url(API_URL)
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
                return;
            }

            // 逐行读取流式响应（SSE 格式）
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(responseBody.byteStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 过滤空行和注释行（SSE 协议规范）
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }

                    // 解析 SSE 格式数据（data: {JSON内容}）
                    if (line.startsWith("data: ")) {
                        String jsonData = line.substring(6); // 去掉 "data: " 前缀

                        // 处理结束标识（data: [DONE]）
                        if ("[DONE]".equals(jsonData)) {
                            System.out.println("流式响应结束");
                            break;
                        }

                        // 解析 JSON 数据并输出
                        try {
                            JSONObject dataObj = JSONObject.parseObject(jsonData);
                            System.out.println("收到流式数据: " + dataObj.toJSONString());

                            // 可根据实际返回结构提取具体内容，例如：
                            // if (dataObj.containsKey("content")) {
                            //     System.out.println("回复内容: " + dataObj.getString("content"));
                            // }
                        } catch (Exception e) {
                            System.err.println("解析JSON失败: " + jsonData);
                            e.printStackTrace();
                        }
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("调用API时发生异常:");
            e.printStackTrace();
        }
    }
}
