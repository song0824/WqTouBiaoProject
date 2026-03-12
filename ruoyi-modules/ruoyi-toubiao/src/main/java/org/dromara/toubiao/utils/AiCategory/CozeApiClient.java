package org.dromara.toubiao.utils.AiCategory;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.dromara.toubiao.domain.CategoryMessageDTO;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class CozeApiClient {

    private static final String WORKFLOW_API_URL = "https://api.coze.cn/v1/workflow/stream_run";
    private static final String TOKEN = "sat_iP6XouHa6uHRKsdyCVjXdxOX5AAHbwUevEH38fRWNAWVgmdoELjmdnV5PsDqNz6m";
    private static final String WORKFLOW_ID = "7615849266985517099";

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public List<CategoryMessageDTO> classifyByWorkflow(String id, String message, String name, String needs) throws IOException {
        List<CategoryMessageDTO> categoryList = new ArrayList<>();

        // 构建请求
        JSONObject requestBody = new JSONObject();
        requestBody.put("workflow_id", WORKFLOW_ID);

        JSONObject parameters = new JSONObject();
        parameters.put("id", id);
        parameters.put("message", message);
        parameters.put("name", name);
        parameters.put("needs", needs);
        requestBody.put("parameters", parameters);

        Request request = new Request.Builder()
            .url(WORKFLOW_API_URL)
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody.toJSONString()))
            .addHeader("Authorization", "Bearer " + TOKEN)
            .addHeader("Content-Type", "application/json")
            .build();

        long startTime = System.currentTimeMillis();
        System.out.println("【开始请求】项目ID：" + id);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败，HTTP状态码：" + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                System.out.println("【响应为空】项目ID：" + id);
                return categoryList;
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), "UTF-8"))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith(":")) continue;

                    String[] parts = line.split(":", 2);
                    if (parts.length < 2) continue;

                    String field = parts[0].trim();
                    String content = parts[1].trim();

                    // 解析返回数据
                    if ("data".equals(field)) {
                        try {
                            JSONObject dataJson = JSONObject.parseObject(content);
                            String resultContent = dataJson.getString("content");
                            if (resultContent == null) continue;

                            JSONObject resultJson = JSONObject.parseObject(resultContent);
                            JSONArray outputArray = resultJson.getJSONArray("output");

                            if (outputArray != null && !outputArray.isEmpty()) {
                                System.out.println("======================================");
                                System.out.println("项目ID：" + id + " 返回分类数量：" + outputArray.size());

                                for (int i = 0; i < outputArray.size(); i++) {
                                    JSONObject item = outputArray.getJSONObject(i);

                                    CategoryMessageDTO dto = new CategoryMessageDTO();
                                    dto.setProjectId(item.getString("id"));
                                    dto.setCodeLevel1(item.getString("code_level1"));
                                    dto.setNameLevel1(item.getString("name_level1"));
                                    dto.setCodeLevel2(item.getString("code_level2"));
                                    dto.setNameLevel2(item.getString("name_level2"));
                                    dto.setCodeLevel3(item.getString("code_level3"));
                                    dto.setNameLevel3(item.getString("name_level3"));
                                    dto.setIsClassifyed(item.getString("is_classifyed"));

                                    categoryList.add(dto);
                                }
                                System.out.println("【解析成功】项目ID：" + id + "，共" + categoryList.size() + "条");
                                System.out.println("======================================");
                            }

                        } catch (Exception e) {
                            System.out.println("【解析异常】项目ID：" + id + " → " + e.getMessage());
                        }
                    }
                }
            }
        }

        long costTime = (System.currentTimeMillis() - startTime);
        if (categoryList.isEmpty()) {
            System.out.println("【请求完成】项目ID：" + id + " → 未获取到分类数据");
        } else {
            System.out.println("【请求完成】项目ID：" + id + " → 成功解析" + categoryList.size() + "条");
        }

        return categoryList;
    }
}
