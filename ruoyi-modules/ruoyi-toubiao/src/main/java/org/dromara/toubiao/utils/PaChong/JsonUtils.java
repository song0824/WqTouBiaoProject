package org.dromara.toubiao.utils.PaChong;

/**
 * 功能：
 * 作者：张
 * 日期：2025/12/24 19:49
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.toubiao.domain.TenderProjectDetail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类
 */
public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 JSON 字符串中提取 Table 数组并转换为对象列表
     */
    public static List<TenderProjectDetail> parseKaiBiaoData(String jsonData) {
        try {
            // 解析整个 JSON
            Map<String, Object> resultMap = objectMapper.readValue(jsonData,
                    new TypeReference<Map<String, Object>>() {});

            // 获取 Table 数组
            List<Map<String, Object>> tableList = (List<Map<String, Object>>) resultMap.get("Table");

            // 转换为 TenderProjectDetail 对象
            List<TenderProjectDetail> projects = new ArrayList<>();

            for (Map<String, Object> item : tableList) {
                TenderProjectDetail project = new TenderProjectDetail();
                project.setArea((String) item.get("area"));
                project.setChangdi((String) item.get("changdi"));
                project.setProno((String) item.get("prono"));
                project.setInfoid((String) item.get("infoid"));
                project.setKaibiaodate(LocalDateTime.parse((String) item.get("kaibiaodate")));
                project.setProname((String) item.get("proname"));
                project.setBak((String) item.get("bak"));
                projects.add(project);
            }

            return projects;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 提取特定属性到数组
     */
    public static List<String> extractProperty(List<TenderProjectDetail> projects,
                                               String propertyName) {
        List<String> result = new ArrayList<>();

        for (TenderProjectDetail project : projects) {
            switch (propertyName) {
                case "area":
                    result.add(project.getArea());
                    break;
                case "projectName":
                    result.add(project.getProname());
                    break;
                case "projectNo":
                    result.add(project.getProno());
                    break;
                case "kaibiaoDate":
                    result.add(project.getKaibiaodate().toString());
                    break;
                default:
                    result.add("未知属性");
            }
        }
        return result;
    }
}
