package org.dromara.toubiao.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.extern.slf4j.Slf4j;
import org.dromara.toubiao.domain.TenderProjectDetail;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.dromara.toubiao.mapper.TenderParsedMapper;
import org.dromara.toubiao.parser.HebeiPageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面解析测试控制器
 *
 * @author 张
 * @date 2025-12-30
 */
@RestController
@RequestMapping("/test/parse")
@Slf4j
public class TenderParseTestController {

    @Autowired
    private TenderParsedMapper tenderParsedMapper;

    @Autowired
    private HebeiPageParser pageParser;

    /**
     * 测试1：检查Mapper是否正常
     */
    @SaIgnore
    @GetMapping("/check-mapper")
    public Map<String, Object> checkMapper() {
        Map<String, Object> result = new HashMap<>();

        try {
            List<TenderProjectDetail> list = tenderParsedMapper.selectUnparsedList(5);

            result.put("success", true);
            result.put("message", "✅ Mapper工作正常");
            result.put("待解析记录数", list.size());
            result.put("记录列表", list);

            log.info("Mapper测试成功，查询到 {} 条待解析记录", list.size());

        } catch (Exception e) {
            log.error("Mapper测试失败", e);
            result.put("success", false);
            result.put("message", "❌ Mapper测试失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试2：解析单个页面（自动选择）
     */
    @SaIgnore
    @GetMapping("/single")
    public Map<String, Object> parseSingle() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("========== 开始测试页面解析 ==========");

            // 1. 查询待解析记录
            List<TenderProjectDetail> list = tenderParsedMapper.selectUnparsedList(1);

            if (list.isEmpty()) {
                result.put("success", false);
                result.put("message", "❌ 没有待解析的记录");
                return result;
            }

            TenderProjectDetail detail = list.get(0);
            log.info("找到待解析记录：{}", detail.getProname());

            // 2. 解析页面
            long startTime = System.currentTimeMillis();
            TenderProjectDetailParsed parsed = pageParser.parse(
                detail.getInfoid(),
                detail.getInfoUrl()
            );
            long endTime = System.currentTimeMillis();

            // 3. 组装返回结果
            result.put("success", parsed.getParseStatus() == 2);
            result.put("解析耗时", (endTime - startTime) + "ms");
            result.put("解析状态", parsed.getParseStatus() == 2 ? "✅ 成功" : "❌ 失败");

            if (parsed.getParseStatus() == 2) {
                // 解析成功
                Map<String, Object> basicInfo = new HashMap<>();
                basicInfo.put("infoid", parsed.getInfoid());
                basicInfo.put("项目编号", parsed.getProno());
                basicInfo.put("项目名称", parsed.getProname());
                basicInfo.put("预算金额", parsed.getBudgetAmount());
                basicInfo.put("采购方式", parsed.getTenderMethod());
                basicInfo.put("项目地区", parsed.getArea());
                basicInfo.put("发布时间", parsed.getPublishTime());
                basicInfo.put("开标时间", parsed.getKaibiaodate());
                basicInfo.put("开标地点", parsed.getChangdi());
                basicInfo.put("采购人", parsed.getPurchaser());
                basicInfo.put("采购人地址", parsed.getPurchaserAddress());
                basicInfo.put("采购人电话", parsed.getPurchaserPhone());
                basicInfo.put("代理机构", parsed.getAgentCompany());
                basicInfo.put("代理机构地址", parsed.getAgentAddress());
                basicInfo.put("代理机构电话", parsed.getAgentPhone());
                basicInfo.put("项目联系人", parsed.getProjectContact());
                basicInfo.put("项目联系电话", parsed.getProjectPhone());

                // 章节提取情况统计
                Map<String, String> sections = new HashMap<>();
                sections.put("采购需求", parsed.getSectionProjectNeed() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("项目概况", parsed.getSectionProjectOverview() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("基本情况", parsed.getSectionBasicInfo() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("资格要求", parsed.getSectionQualification() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("获取文件", parsed.getSectionDocAcquisition() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("投标时间地点", parsed.getSectionBiddingSchedule() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("公告期限", parsed.getSectionAnnouncementPeriod() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("其他事项", parsed.getSectionOtherMatters() != null ? "✅ 已提取" : "❌ 未提取");
                sections.put("联系方式", parsed.getSectionContact() != null ? "✅ 已提取" : "❌ 未提取");

                result.put("基本信息", basicInfo);
                result.put("章节提取情况", sections);

                // 统计提取成功的章节数
                long successCount = sections.values().stream().filter(v -> v.contains("✅")).count();
                result.put("章节提取统计", successCount + " / " + sections.size());

                // 完整数据（方便调试）
                result.put("完整数据", parsed);

                log.info("✅ 解析成功：{}", parsed.getProname());

            } else {
                // 解析失败
                result.put("失败原因", parsed.getParseErrorMsg());
                log.error("❌ 解析失败：{}", parsed.getParseErrorMsg());
            }

            log.info("========== 测试完成 ==========");

        } catch (Exception e) {
            log.error("测试解析异常", e);
            result.put("success", false);
            result.put("message", "❌ 测试失败: " + e.getMessage());
            result.put("异常堆栈", e.toString());
        }

        return result;
    }

    /**
     * 测试3：解析指定URL
     */
    @SaIgnore
    @PostMapping("/by-url")
    public Map<String, Object> parseByUrl(@RequestParam String url) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("解析指定URL: {}", url);

            // 解析
            String testInfoId = "test_" + System.currentTimeMillis();
            TenderProjectDetailParsed parsed = pageParser.parse(testInfoId, url);

            result.put("success", parsed.getParseStatus() == 2);
            result.put("解析状态", parsed.getParseStatus() == 2 ? "✅ 成功" : "❌ 失败");
            result.put("项目名称", parsed.getProname());
            result.put("完整数据", parsed);

            if (parsed.getParseStatus() == 3) {
                result.put("失败原因", parsed.getParseErrorMsg());
            }

        } catch (Exception e) {
            log.error("解析失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 测试4：解析并保存到数据库
     */
    @SaIgnore
    @PostMapping("/parse-and-save")
    public Map<String, Object> parseAndSave() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 查询待解析记录
            List<TenderProjectDetail> list = tenderParsedMapper.selectUnparsedList(1);

            if (list.isEmpty()) {
                result.put("success", false);
                result.put("message", "没有待解析的记录");
                return result;
            }

            TenderProjectDetail detail = list.get(0);

            // 2. 解析
            TenderProjectDetailParsed parsed = pageParser.parse(
                detail.getInfoid(),
                detail.getInfoUrl()
            );

            // 3. 保存到数据库
            if (parsed.getParseStatus() == 2) {
                // 检查是否已存在
                TenderProjectDetailParsed existing = tenderParsedMapper.selectByInfoId(detail.getInfoid());

                int saveResult;
                if (existing != null) {
                    // 更新
                    parsed.setId(existing.getId());
                    saveResult = tenderParsedMapper.update(parsed);
                    result.put("操作", "更新");
                } else {
                    // 插入
                    saveResult = tenderParsedMapper.insert(parsed);
                    result.put("操作", "插入");
                }

                result.put("success", saveResult > 0);
                result.put("message", saveResult > 0 ? "✅ 保存成功" : "❌ 保存失败");
                result.put("影响行数", saveResult);
                result.put("数据", parsed);

            } else {
                result.put("success", false);
                result.put("message", "解析失败，未保存");
                result.put("失败原因", parsed.getParseErrorMsg());
            }

        } catch (Exception e) {
            log.error("保存失败", e);
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }

        return result;
    }
}
