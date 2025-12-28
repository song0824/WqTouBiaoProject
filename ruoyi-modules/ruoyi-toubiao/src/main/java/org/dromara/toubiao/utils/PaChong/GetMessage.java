package org.dromara.toubiao.utils.PaChong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dromara.toubiao.domain.KaibiaoResponse;
import org.dromara.toubiao.domain.TenderProjectDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class GetMessage {

    // 正确初始化 ObjectMapper
    @Autowired
    private ObjectMapper objectMapper;  // 使用 Spring 配置的 ObjectMapper

    public GetMessage() {
        // 创建自定义的日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // 配置 JavaTimeModule
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));

        // 构建 ObjectMapper
        this.objectMapper = JsonMapper.builder()
                .addModule(module)
                .build();
    }

    public String getData() {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        // 添加时间戳防止缓存
        String urlWithTimestamp = "http://ssl.hebpr.cn/hbggfwpt/json/Kaibiao/threedate.json" + "?_=" + System.currentTimeMillis();
        HttpGet request = new HttpGet(urlWithTimestamp);

        // 添加必要的请求头
        request.setHeader("Referer", "http://ssl.hebpr.cn/hbggfwpt/jydt/salesPlat.html");
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36");
        request.setHeader("Accept", "application/json, text/javascript, */*; q=0.01");
        request.setHeader("X-Requested-With", "XMLHttpRequest"); // 可选，但建议加上

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public KaibiaoResponse getKaiBiaoResponse() {
        try {
            String jsonData = getData();
            if (jsonData != null) {
                return objectMapper.readValue(jsonData, KaibiaoResponse.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取项目详情列表
     */
    public List<TenderProjectDetail> getTenderProjectList() {
        KaibiaoResponse response = getKaiBiaoResponse();
        if (response != null && response.getTable() != null) {
            return response.getTable();
        }
        return new ArrayList<>();
    }



}
