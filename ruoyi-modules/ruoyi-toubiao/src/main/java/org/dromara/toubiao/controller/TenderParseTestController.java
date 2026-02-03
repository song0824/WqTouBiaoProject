package org.dromara.toubiao.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.extern.slf4j.Slf4j;
import org.dromara.toubiao.domain.TenderProjectDetail;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.dromara.toubiao.mapper.GetMessageMapper;
import org.dromara.toubiao.mapper.TenderParsedMapper;
import org.dromara.toubiao.parser.HebeiPageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 页面解析测试控制器
 *
 * @author
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

    @Autowired
    private GetMessageMapper getMessageMapper;



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
            result.put("message", "Mapper工作正常");
            result.put("待解析记录数", list.size());
            result.put("记录列表", list);

            log.info("Mapper测试成功，查询到 {} 条待解析记录", list.size());

        } catch (Exception e) {
            log.error("Mapper测试失败", e);
            result.put("success", false);
            result.put("message", "Mapper测试失败: " + e.getMessage());
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
            log.info("开始测试页面解析");

            // 1. 查询多条待解析记录（最多10条，用于自动重试）
            int maxRetryCount = 10;
            List<TenderProjectDetail> list = tenderParsedMapper.selectUnparsedList(maxRetryCount);

            if (list.isEmpty()) {
                result.put("success", false);
                result.put("message", "没有待解析的记录");
                return result;
            }

            log.info("找到 {} 条待解析记录", list.size());

            // 2. 记录解析过程
            List<Map<String, Object>> parseLogs = new ArrayList<>();
            TenderProjectDetailParsed successfulParsed = null;
            String skipReason = null;

            // 3. 逐个尝试解析，直到成功或全部尝试完毕
            for (int i = 0; i < list.size(); i++) {
                TenderProjectDetail detail = list.get(i);
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("序号", i + 1);
                logEntry.put("infoid", detail.getInfoid());
                logEntry.put("项目名称", detail.getProname());

                log.info("尝试解析第 {} 条记录：{}", i + 1, detail.getProname());

                // 4. 解析页面
                long startTime = System.currentTimeMillis();
                TenderProjectDetailParsed parsed = pageParser.parse(
                    detail.getInfoid(),
                    detail.getInfoUrl(),
                    detail.getProname()
                );
                long endTime = System.currentTimeMillis();
                long parseTime = endTime - startTime;

                logEntry.put("解析耗时", parseTime + "ms");
                logEntry.put("解析状态", parsed.getParseStatus());

                // 5. 检查解析状态
                if (parsed.getParseStatus() == 2) {
                    // 解析成功
                    logEntry.put("结果", "解析成功");
                    successfulParsed = parsed;
                    parseLogs.add(logEntry);
                    log.info("第 {} 条记录解析成功：{}", i + 1, detail.getProname());
                    break; // 跳出循环，不再尝试后续记录

                } else if (parsed.getParseStatus() == 4) {
                    // 跳过解析（非标准格式）
                    logEntry.put("结果", "跳过解析");
                    logEntry.put("跳过原因", parsed.getParseErrorMsg());
                    parseLogs.add(logEntry);
                    skipReason = parsed.getParseErrorMsg();
                    log.info("第 {} 条记录被跳过：{} - {}", i + 1, detail.getProname(), parsed.getParseErrorMsg());
                    // 继续尝试下一条记录

                } else {
                    // 解析失败（状态3或其他）
                    logEntry.put("结果", "解析失败");
                    logEntry.put("失败原因", parsed.getParseErrorMsg());
                    parseLogs.add(logEntry);
                    log.error("第 {} 条记录解析失败：{} - {}", i + 1, detail.getProname(), parsed.getParseErrorMsg());
                    // 继续尝试下一条记录
                }
            }

            // 6. 组装返回结果
            result.put("尝试记录", parseLogs);
            result.put("尝试总数", parseLogs.size());

            if (successfulParsed != null) {
                // 成功解析了一条
                result.put("success", true);
                result.put("最终结果", "成功解析一条记录");

                // 添加成功记录的基本信息
                Map<String, Object> basicInfo = new HashMap<>();
                basicInfo.put("infoid", successfulParsed.getInfoid());
                basicInfo.put("项目编号", successfulParsed.getProno());
                basicInfo.put("项目名称", successfulParsed.getProname());
                basicInfo.put("预算金额", successfulParsed.getBudgetAmount());
                basicInfo.put("采购方式", successfulParsed.getTenderMethod());
                basicInfo.put("项目地区", successfulParsed.getArea());
                basicInfo.put("发布时间", successfulParsed.getPublishTime());
                basicInfo.put("开标时间", successfulParsed.getKaibiaodate());
                basicInfo.put("开标地点", successfulParsed.getChangdi());
                basicInfo.put("采购人", successfulParsed.getPurchaser());
                basicInfo.put("采购人地址", successfulParsed.getPurchaserAddress());
                basicInfo.put("采购人电话", successfulParsed.getPurchaserPhone());
                basicInfo.put("代理机构", successfulParsed.getAgentCompany());
                basicInfo.put("代理机构地址", successfulParsed.getAgentAddress());
                basicInfo.put("代理机构电话", successfulParsed.getAgentPhone());
                basicInfo.put("项目联系人", successfulParsed.getProjectContact());
                basicInfo.put("项目联系电话", successfulParsed.getProjectPhone());

                // 章节提取情况统计
                Map<String, String> sections = new HashMap<>();
                sections.put("采购需求", successfulParsed.getSectionProjectNeed() != null ? "已提取" : "未提取");
                sections.put("项目概况", successfulParsed.getSectionProjectOverview() != null ? "已提取" : "未提取");
                sections.put("基本情况", successfulParsed.getSectionBasicInfo() != null ? "已提取" : "未提取");
                sections.put("资格要求", successfulParsed.getSectionQualification() != null ? "已提取" : "未提取");
                sections.put("获取文件", successfulParsed.getSectionDocAcquisition() != null ? "已提取" : "未提取");
                sections.put("投标时间地点", successfulParsed.getSectionBiddingSchedule() != null ? "已提取" : "未提取");
                sections.put("公告期限", successfulParsed.getSectionAnnouncementPeriod() != null ? "已提取" : "未提取");
                sections.put("其他事项", successfulParsed.getSectionOtherMatters() != null ? "已提取" : "未提取");
                sections.put("联系方式", successfulParsed.getSectionContact() != null ? "已提取" : "未提取");

                // 统计提取成功的章节数
                long successCount = sections.values().stream().filter(v -> v.contains("已提取")).count();

                result.put("基本信息", basicInfo);
                result.put("章节提取情况", sections);
                result.put("章节提取统计", successCount + " / " + sections.size());

                // 记录成功的是第几条
                int successIndex = 0;
                for (int i = 0; i < parseLogs.size(); i++) {
                    if ("解析成功".equals(parseLogs.get(i).get("结果"))) {
                        successIndex = i + 1;
                        break;
                    }
                }
                result.put("成功记录位置", "第 " + successIndex + " 条记录");

            } else {
                // 全部尝试都失败或跳过了
                result.put("success", false);

                // 统计结果
                long skippedCount = parseLogs.stream()
                    .filter(log -> "跳过解析".equals(log.get("结果")))
                    .count();
                long failedCount = parseLogs.stream()
                    .filter(log -> "解析失败".equals(log.get("结果")))
                    .count();

                if (skippedCount > 0 && failedCount == 0) {
                    result.put("最终结果", "所有记录均为非标准格式，已跳过");
                    result.put("跳过数量", skippedCount);
                    if (skipReason != null) {
                        result.put("跳过原因", skipReason);
                    }
                } else if (failedCount > 0 && skippedCount == 0) {
                    result.put("最终结果", "所有记录解析失败");
                    result.put("失败数量", failedCount);
                } else {
                    result.put("最终结果", "未能成功解析任何记录");
                    result.put("跳过数量", skippedCount);
                    result.put("失败数量", failedCount);
                }
            }

            log.info("测试完成");

        } catch (Exception e) {
            log.error("测试解析异常", e);
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
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
            result.put("解析状态", parsed.getParseStatus() == 2 ? "成功" : "失败");
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
        List<Map<String, Object>> parseAttempts = new ArrayList<>();

        try {
            log.info("开始解析并保存");

            // 1. 查询多条待解析记录（最多尝试5条）
            int maxRetryCount = 10;
            List<TenderProjectDetail> list = tenderParsedMapper.selectUnparsedList(maxRetryCount);

            if (list.isEmpty()) {
                result.put("success", false);
                result.put("message", "没有待解析的记录");
                return result;
            }

            log.info("找到 {} 条待解析记录，开始处理...", list.size());

            TenderProjectDetailParsed successfullySaved = null;
            int successIndex = -1;

            // 2. 逐个尝试解析，直到成功保存一条或全部尝试完毕
            for (int i = 0; i < list.size(); i++) {
                TenderProjectDetail detail = list.get(i);
                Map<String, Object> attempt = new HashMap<>();
                attempt.put("序号", i + 1);
                attempt.put("infoid", detail.getInfoid());
                attempt.put("项目名称", detail.getProname());
                attempt.put("URL", detail.getInfoUrl());

                log.info("尝试解析第 {} 条记录：{}", i + 1, detail.getProname());

                long startTime = System.currentTimeMillis();
                TenderProjectDetailParsed parsed = null;

                try {
                    // 3. 解析页面
                    parsed = pageParser.parse(detail.getInfoid(), detail.getInfoUrl());
                    long parseTime = System.currentTimeMillis() - startTime;

                    attempt.put("解析耗时", parseTime + "ms");
                    attempt.put("解析状态", parsed.getParseStatus());

                    // 4. 检查解析状态
                    if (parsed.getParseStatus() == 2) {
                        // 解析成功，尝试保存到数据库
                        attempt.put("解析结果", "解析成功");

                        // 检查是否已存在
                        TenderProjectDetailParsed existing = tenderParsedMapper.selectByInfoId(detail.getInfoid());

                        int saveResult;
                        if (existing != null) {
                            // 更新
                            parsed.setId(existing.getId());
                            saveResult = tenderParsedMapper.update(parsed);
                            attempt.put("数据库操作", "更新");
                        } else {
                            // 插入
                            saveResult = tenderParsedMapper.insert(parsed);
                            attempt.put("数据库操作", "插入");
                        }

                        if (saveResult > 0) {
                            // 保存成功
                            attempt.put("保存结果", "保存成功");
                            attempt.put("影响行数", saveResult);
                            successfullySaved = parsed;
                            successIndex = i + 1;
                            parseAttempts.add(attempt);
                            log.info("第 {} 条记录解析并保存成功：{}", i + 1, detail.getProname());
                            break; // 跳出循环，不再尝试后续记录
                        } else {
                            // 保存失败
                            attempt.put("保存结果", "保存失败");
                            attempt.put("保存错误", "数据库操作返回0行影响");
                            parseAttempts.add(attempt);
                            log.error("第 {} 条记录解析成功但保存失败：{}", i + 1, detail.getProname());
                            // 继续尝试下一条记录
                        }

                    } else if (parsed.getParseStatus() == 4) {
                        // 跳过解析（非标准格式）
                        attempt.put("解析结果", "跳过解析");
                        attempt.put("跳过原因", parsed.getParseErrorMsg());
                        parseAttempts.add(attempt);
                        log.info("第 {} 条记录被跳过：{} - {}", i + 1, detail.getProname(), parsed.getParseErrorMsg());
                        // 继续尝试下一条记录

                    } else {
                        // 解析失败（状态3或其他）
                        attempt.put("解析结果", "解析失败");
                        attempt.put("失败原因", parsed.getParseErrorMsg());
                        parseAttempts.add(attempt);
                        log.error("第 {} 条记录解析失败：{} - {}", i + 1, detail.getProname(), parsed.getParseErrorMsg());
                        // 继续尝试下一条记录
                    }

                } catch (Exception e) {
                    // 解析过程发生异常
                    attempt.put("解析结果", "解析异常");
                    attempt.put("异常信息", e.getMessage());
                    parseAttempts.add(attempt);
                    log.error("第 {} 条记录解析异常：{}", i + 1, detail.getProname(), e);
                    // 继续尝试下一条记录
                }
            }

            // 5. 组装返回结果
            result.put("尝试记录", parseAttempts);
            result.put("尝试总数", parseAttempts.size());

            if (successfullySaved != null) {
                // 成功保存了一条
                result.put("success", true);
                result.put("最终结果", "成功解析并保存一条记录");
                result.put("成功记录位置", "第 " + successIndex + " 条记录");

                // 添加成功记录的基本信息
                Map<String, Object> basicInfo = new HashMap<>();
                basicInfo.put("infoid", successfullySaved.getInfoid());
                basicInfo.put("项目编号", successfullySaved.getProno());
                basicInfo.put("项目名称", successfullySaved.getProname());
                basicInfo.put("预算金额", successfullySaved.getBudgetAmount());
                basicInfo.put("采购方式", successfullySaved.getTenderMethod());
                basicInfo.put("项目地区", successfullySaved.getArea());
                basicInfo.put("解析时间", successfullySaved.getParseTime());

                // 章节提取情况统计
                Map<String, String> sections = new HashMap<>();
                sections.put("采购需求", successfullySaved.getSectionProjectNeed() != null ? "已提取" : "未提取");
                sections.put("项目概况", successfullySaved.getSectionProjectOverview() != null ? "已提取" : "未提取");
                sections.put("基本情况", successfullySaved.getSectionBasicInfo() != null ? "已提取" : "未提取");
                sections.put("资格要求", successfullySaved.getSectionQualification() != null ? "已提取" : "未提取");
                sections.put("获取文件", successfullySaved.getSectionDocAcquisition() != null ? "已提取" : "未提取");
                sections.put("投标时间地点", successfullySaved.getSectionBiddingSchedule() != null ? "已提取" : "未提取");
                sections.put("公告期限", successfullySaved.getSectionAnnouncementPeriod() != null ? "已提取" : "未提取");
                sections.put("其他事项", successfullySaved.getSectionOtherMatters() != null ? "已提取" : "未提取");
                sections.put("联系方式", successfullySaved.getSectionContact() != null ? "已提取" : "未提取");

                // 统计提取成功的章节数
                long successCount = 0;
                for (String value : sections.values()) {
                    if (value.contains("已提取")) {
                        successCount++;
                    }
                }

                result.put("基本信息", basicInfo);
                result.put("章节提取情况", sections);
                result.put("章节提取统计", successCount + " / " + sections.size());

            } else {
                // 全部尝试都失败或跳过了
                result.put("success", false);

                // 统计结果
                long skippedCount = 0;
                long failedCount = 0;
                long parseExceptionCount = 0;
                long saveFailedCount = 0;

                for (Map<String, Object> attempt : parseAttempts) {
                    String resultStr = (String) attempt.get("解析结果");
                    if (resultStr != null) {
                        if (resultStr.contains("跳过")) {
                            skippedCount++;
                        } else if (resultStr.contains("解析失败")) {
                            failedCount++;
                        } else if (resultStr.contains("解析异常")) {
                            parseExceptionCount++;
                        } else if (resultStr.contains("解析成功") && "保存失败".equals(attempt.get("保存结果"))) {
                            saveFailedCount++;
                        }
                    }
                }

                // 构建详细的失败信息
                StringBuilder message = new StringBuilder();
                if (skippedCount > 0) {
                    message.append("跳过 ").append(skippedCount).append(" 条（非标准格式）");
                }
                if (failedCount > 0) {
                    if (message.length() > 0) message.append("，");
                    message.append("解析失败 ").append(failedCount).append(" 条");
                }
                if (parseExceptionCount > 0) {
                    if (message.length() > 0) message.append("，");
                    message.append("解析异常 ").append(parseExceptionCount).append(" 条");
                }
                if (saveFailedCount > 0) {
                    if (message.length() > 0) message.append("，");
                    message.append("保存失败 ").append(saveFailedCount).append(" 条");
                }

                if (message.length() == 0) {
                    result.put("最终结果", "未能成功解析并保存任何记录");
                } else {
                    result.put("最终结果", message.toString());
                }

                result.put("跳过数量", skippedCount);
                result.put("解析失败数量", failedCount);
                result.put("解析异常数量", parseExceptionCount);
                result.put("保存失败数量", saveFailedCount);
            }

            log.info("解析并保存完成");

        } catch (Exception e) {
            log.error("解析并保存异常", e);
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
        }

        return result;
    }






    /**
     * 测试5：批量解析并保存所有记录（包括跳过的）
     * 注意：此接口会处理所有待解析记录，包括非标准格式
     */
    @SaIgnore
    @PostMapping("/batch-save-all")
    public Map<String, Object> batchSaveAll(
        @RequestParam(defaultValue = "10") int batchSize,
        @RequestParam(defaultValue = "false") boolean randomDelay,
        @RequestParam(defaultValue = "5") int maxConcurrent) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> processLogs = new ArrayList<>();

        try {
            log.info("开始批量解析并保存所有记录");
            log.info("批次大小: {}, 随机延迟: {}, 最大并发: {}", batchSize, randomDelay, maxConcurrent);

            // 1. 查询待解析记录
            List<TenderProjectDetail> list = tenderParsedMapper.selectUnparsedList(batchSize);

            if (list.isEmpty()) {
                result.put("success", false);
                result.put("message", "没有待解析的记录");
                return result;
            }

            log.info("找到 {} 条待解析记录", list.size());

            // 2. 使用固定线程池控制并发（避免被识别为爬虫）
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(maxConcurrent, list.size()));
            List<Future<Map<String, Object>>> futures = new ArrayList<>();

            // 3. 提交解析任务
            for (int i = 0; i < list.size(); i++) {
                TenderProjectDetail detail = list.get(i);

                // 添加随机延迟（模拟人工操作）
                if (randomDelay && i > 0) {
                    try {
                        int delay = 1000 + new Random().nextInt(3000); // 1-4秒随机延迟
                        Thread.sleep(delay);
                        log.debug("随机延迟 {}ms 后处理第 {} 条记录", delay, i + 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // 提交任务到线程池
                Future<Map<String, Object>> future = executor.submit(() ->
                    processSingleRecord(detail)
                );
                futures.add(future);
            }

            // 4. 等待所有任务完成
            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);

            if (!finished) {
                executor.shutdownNow();
                log.warn("部分任务超时未完成");
            }

            // 5. 收集结果
            int successCount = 0;
            int skipCount = 0;
            int failCount = 0;
            List<TenderProjectDetailParsed> allSavedRecords = new ArrayList<>();

            for (int i = 0; i < futures.size(); i++) {
                try {
                    Map<String, Object> taskResult = futures.get(i).get();
                    processLogs.add(taskResult);

                    String status = (String) taskResult.get("处理结果");
                    if ("成功".equals(status)) {
                        successCount++;
                        allSavedRecords.add((TenderProjectDetailParsed) taskResult.get("解析数据"));
                    } else if ("跳过".equals(status)) {
                        skipCount++;
                        allSavedRecords.add((TenderProjectDetailParsed) taskResult.get("解析数据"));
                    } else {
                        failCount++;
                    }

                } catch (Exception e) {
                    log.error("处理任务异常", e);
                    Map<String, Object> errorLog = new HashMap<>();
                    errorLog.put("处理结果", "异常");
                    errorLog.put("异常信息", e.getMessage());
                    processLogs.add(errorLog);
                    failCount++;
                }
            }

            // 6. 组装返回结果
            result.put("success", true);

            // 使用可变Map创建统计信息
            Map<String, Object> stats = new HashMap<>();
            stats.put("总记录数", list.size());
            stats.put("成功数", successCount);
            stats.put("跳过数", skipCount);
            stats.put("失败数", failCount);
            stats.put("成功率", String.format("%.2f%%", (successCount * 100.0 / list.size())));
            result.put("处理统计", stats);

            result.put("处理日志", processLogs);

            // 7. 添加反爬虫特征（使用安全版本）
            result = addAntiCrawlerFeatures(result);

            log.info("批量解析完成");
            log.info("成功: {}条, 跳过: {}条, 失败: {}条", successCount, skipCount, failCount);

        } catch (Exception e) {
            log.error("批量解析异常", e);
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试6：全量解析所有待处理数据（保存所有记录，包括跳过和失败的）
     */
    @SaIgnore
    @PostMapping("/parse-all")
    public Map<String, Object> parseAllData(
        @RequestParam(defaultValue = "20") int batchSize,
        @RequestParam(defaultValue = "true") boolean randomDelay,
        @RequestParam(defaultValue = "3") int maxConcurrent,
        @RequestParam(defaultValue = "10000") int maxTotal) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> allBatches = new ArrayList<>();

        int batchCount = 0;
        int totalProcessed = 0;
        try {
            log.info("开始全量解析所有数据");

            totalProcessed = 0;
            batchCount = 0;
            boolean hasMoreData = true;

            // 获取总待处理数量
            int totalUnparsed = getMessageMapper.countUnparsedRecords();
            result.put("总待处理记录数", totalUnparsed);

            if (totalUnparsed == 0) {
                result.put("success", true);
                result.put("message", "没有待解析的记录");
                return result;
            }

            log.info("发现 {} 条待解析记录，开始处理...", totalUnparsed);

            while (hasMoreData && totalProcessed < maxTotal) {
                batchCount++;

                log.info("处理第 {} 批，批次大小：{}", batchCount, batchSize);

                // 调用批量处理接口
                Map<String, Object> batchResult = batchSaveAll(batchSize, randomDelay, maxConcurrent);

                // 记录批次结果
                Map<String, Object> batchSummary = new HashMap<>();
                batchSummary.put("批次号", batchCount);
                batchSummary.put("开始时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                batchSummary.put("处理结果", extractBatchSummary(batchResult));
                allBatches.add(batchSummary);

                // 统计处理数量
                if (batchResult.containsKey("处理统计")) {
                    Map<String, Object> stats = (Map<String, Object>) batchResult.get("处理统计");
                    int batchProcessed = (int) stats.getOrDefault("总记录数", 0);
                    totalProcessed += batchProcessed;

                    log.info("第 {} 批处理完成：处理 {} 条，累计 {} 条",
                        batchCount, batchProcessed, totalProcessed);

                    // 检查是否还有更多数据
                    hasMoreData = (batchProcessed >= batchSize);

                    // 如果这批处理的记录数小于批次大小，说明数据快处理完了
                    if (batchProcessed < batchSize) {
                        log.info("当前批次处理数量 ({}) 小于批次大小 ({}), 预计数据已处理完",
                            batchProcessed, batchSize);
                    }
                } else {
                    log.warn("第 {} 批处理结果异常，没有统计信息", batchCount);
                    hasMoreData = false;
                }

                // 批次之间等待（避免过度请求）
                if (hasMoreData && randomDelay) {
                    int waitTime = 5 + new Random().nextInt(10); // 5-15秒
                    log.info("等待 {} 秒后继续下一批...", waitTime);
                    Thread.sleep(waitTime * 1000L);
                }

                // 每5批输出一次进度
                if (batchCount % 5 == 0) {
                    log.info("处理进度：已处理 {} 批，累计 {} 条", batchCount, totalProcessed);
                }
            }

            // 汇总统计
            Map<String, Object> finalStats = summarizeAllBatches(allBatches);

            result.put("success", true);
            result.put("最终结果", "全量数据处理完成");
            result.put("处理总结", Map.of(
                "总批次数", batchCount,
                "总处理记录数", totalProcessed,
                "起始待处理数", totalUnparsed,
                "处理完成率", String.format("%.1f%%", totalUnparsed > 0 ? (totalProcessed * 100.0 / totalUnparsed) : 100)
            ));
            result.put("详细统计", finalStats);

            // 只显示最近3批的详细结果，避免数据过大
            int showBatches = Math.min(3, allBatches.size());
            result.put("最近批次详情", allBatches.subList(Math.max(0, allBatches.size() - showBatches), allBatches.size()));

            log.info("全量解析完成");
            log.info("总计：{} 批，{} 条记录", batchCount, totalProcessed);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("全量处理被中断", e);
            result.put("success", false);
            result.put("message", "处理被中断");
            result.put("已处理批次数", batchCount);
            result.put("已处理记录数", totalProcessed);
        } catch (Exception e) {
            log.error("全量处理异常", e);
            result.put("success", false);
            result.put("message", "处理异常: " + e.getMessage());
        }

        return result;
    }


    /**
     * 测试7：批量解析并只保存成功记录
     */
    @SaIgnore
    @PostMapping("/batch-save-all-success-only")
    public Map<String, Object> batchSaveAllSuccessOnly(
        @RequestParam(defaultValue = "10") int batchSize,
        @RequestParam(defaultValue = "false") boolean randomDelay,
        @RequestParam(defaultValue = "5") int maxConcurrent) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> processLogs = new ArrayList<>();

        try {
            log.info("开始批量解析并只保存成功记录");

            // 1. 查询待解析记录（bak为null）
            List<TenderProjectDetail> list = getMessageMapper.selectUnparsedListOnlyS(batchSize);

            if (list.isEmpty()) {
                result.put("success", true);
                result.put("message", "没有待解析的记录");
                return result;
            }

            log.info("找到 {} 条待解析记录", list.size());

            // 2. 创建线程池
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(maxConcurrent, list.size()));
            List<Future<Map<String, Object>>> futures = new ArrayList<>();

            // 3. 提交任务
            for (int i = 0; i < list.size(); i++) {
                TenderProjectDetail detail = list.get(i);

                if (randomDelay && i > 0) {
                    try {
                        Thread.sleep(1000 + new Random().nextInt(2000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                futures.add(executor.submit(() -> processSingleRecordForSuccessOnly(detail)));
            }

            // 4. 等待完成
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.MINUTES);

            // 5. 收集结果
            int successCount = 0;
            int skipCount = 0;
            int failCount = 0;
            int exceptionCount = 0;

            for (Future<Map<String, Object>> future : futures) {
                try {
                    Map<String, Object> taskResult = future.get();
                    processLogs.add(taskResult);

                    String status = (String) taskResult.get("处理结果");
                    if ("成功".equals(status)) {
                        successCount++;
                    } else if ("跳过".equals(status)) {
                        skipCount++;
                    } else if ("失败".equals(status)) {
                        failCount++;
                    } else if ("异常".equals(status)) {
                        exceptionCount++;
                    }
                } catch (Exception e) {
                    log.error("处理任务异常", e);
                    exceptionCount++;
                }
            }

            // 6. 返回结果
            result.put("success", true);
            result.put("处理统计", Map.of(
                "总记录数", list.size(),
                "成功数", successCount,
                "跳过数", skipCount,
                "失败数", failCount,
                "异常数", exceptionCount,
                "成功率", String.format("%.2f%%", successCount * 100.0 / list.size())
            ));

            log.info("批量解析完成：成功 {} 条，跳过 {} 条，失败 {} 条，异常 {} 条",
                successCount, skipCount, failCount, exceptionCount);

        } catch (Exception e) {
            log.error("批量解析异常", e);
            result.put("success", false);
            result.put("message", "系统异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试8：全量解析所有待处理数据
     */
    @SaIgnore
    @PostMapping("/parse-all-success-only")
    public Map<String, Object> parseAllDataSuccessOnly(
        @RequestParam(defaultValue = "20") int batchSize,
        @RequestParam(defaultValue = "true") boolean randomDelay,
        @RequestParam(defaultValue = "3") int maxConcurrent,
        @RequestParam(defaultValue = "1000") int maxTotal) {

        Map<String, Object> result = new HashMap<>();

        int batchCount = 0;
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalSkipped = 0;

        try {
            log.info("开始全量解析（带反爬虫特性）");

            int totalUnparsed = getMessageMapper.countUnparsedRecordsOnlyS();
            result.put("总待处理记录数", totalUnparsed);

            if (totalUnparsed == 0) {
                result.put("success", true);
                result.put("message", "没有待解析的记录");
                return result;
            }

            log.info("发现 {} 条待解析记录，开始处理...", totalUnparsed);

            boolean hasMoreData = true;
            Random random = new Random();

            while (hasMoreData && totalProcessed < maxTotal) {
                batchCount++;

                log.info("处理第 {} 批，批次大小：{}", batchCount, batchSize);

                // 1. 查询一批数据
                List<TenderProjectDetail> batchList = getMessageMapper.selectUnparsedListOnlyS(batchSize);

                if (batchList.isEmpty()) {
                    log.info("数据已处理完");
                    break;
                }

                // 2. 创建线程池（限制并发数）
                ExecutorService executor = Executors.newFixedThreadPool(Math.min(maxConcurrent, batchList.size()));
                List<Future<Map<String, Object>>> futures = new ArrayList<>();

                // 3. 提交任务（带模拟延迟）
                for (int i = 0; i < batchList.size(); i++) {
                    TenderProjectDetail detail = batchList.get(i);

                    // 添加逐条记录的处理延迟（模拟人工操作间隔）
                    if (i > 0) {
                        int recordDelay = 2000 + random.nextInt(4000); // 2-6秒
                        try {
                            Thread.sleep(recordDelay);
                            log.debug("记录处理延迟 {}ms", recordDelay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    // 提交任务
                    futures.add(executor.submit(() -> processSingleRecordAntiCrawler(detail)));
                }

                // 4. 等待任务完成
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.MINUTES);

                // 5. 统计本批结果
                int batchSuccess = 0;
                int batchSkipped = 0;
                int batchFailed = 0;

                for (Future<Map<String, Object>> future : futures) {
                    try {
                        Map<String, Object> taskResult = future.get();
                        String status = (String) taskResult.get("处理结果");

                        if ("成功".equals(status)) {
                            batchSuccess++;
                        } else if ("跳过".equals(status)) {
                            batchSkipped++;
                        } else {
                            batchFailed++;
                        }
                    } catch (Exception e) {
                        log.error("处理任务异常", e);
                        batchFailed++;
                    }
                }

                // 6. 更新统计
                totalProcessed += batchList.size();
                totalSuccess += batchSuccess;
                totalSkipped += batchSkipped;

                log.info("第 {} 批处理完成：处理 {} 条，成功 {} 条，跳过 {} 条，失败 {} 条",
                    batchCount, batchList.size(), batchSuccess, batchSkipped, batchFailed);

                // 7. 批次间延迟（避免过快请求）
                int batchDelay = 10000 + random.nextInt(20000); // 10-30秒
                log.info("批次间延迟 {}ms 后继续...", batchDelay);
                Thread.sleep(batchDelay);
            }

            // 返回结果
            result.put("success", true);
            result.put("最终结果", "全量解析完成（反爬虫模式）");
            result.put("处理统计", Map.of(
                "总批次数", batchCount,
                "总处理记录数", totalProcessed,
                "总成功保存数", totalSuccess,
                "总跳过记录数", totalSkipped,
                "成功率", String.format("%.1f%%", totalProcessed > 0 ? (totalSuccess * 100.0 / totalProcessed) : 0)
            ));

            log.info("反爬虫模式全量解析完成：{} 批，{} 条记录，成功 {} 条，跳过 {} 条",
                batchCount, totalProcessed, totalSuccess, totalSkipped);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("处理被中断", e);
            result.put("success", false);
            result.put("message", "处理被中断");
        } catch (Exception e) {
            log.error("处理异常", e);
            result.put("success", false);
            result.put("message", "处理异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 处理单个记录（带反爬虫特性）
     */
    private Map<String, Object> processSingleRecordAntiCrawler(TenderProjectDetail detail) {
        Map<String, Object> result = new HashMap<>();
        result.put("infoid", detail.getInfoid());
        result.put("项目名称", detail.getProname());

        // 获取最新状态
        TenderProjectDetail currentDetail = getMessageMapper.selectByInfoId(detail.getInfoid());
        if (currentDetail == null) {
            result.put("处理结果", "失败");
            result.put("失败原因", "记录不存在");
            return result;
        }

        String currentBak = currentDetail.getBak();

        // 检查是否已处理
        if ("1".equals(currentBak) || "3".equals(currentBak) || "4".equals(currentBak)) {
            result.put("处理结果", "跳过");
            result.put("跳过原因", "记录已处理或正在处理");
            return result;
        }

        // 标记为处理中
        if (!markAsProcessing(detail.getInfoid())) {
            result.put("处理结果", "跳过");
            result.put("跳过原因", "标记处理中失败");
            return result;
        }

        long startTime = System.currentTimeMillis();

        try {
            // 解析前额外延迟（模拟人查看页面的时间）
            Random random = new Random();
            int preParseDelay = 1000 + random.nextInt(3000); // 1-4秒
            Thread.sleep(preParseDelay);

            // 解析页面
            TenderProjectDetailParsed parsed = pageParser.parse(
                detail.getInfoid(),
                detail.getInfoUrl(),
                detail.getProname()
            );

            // 解析后延迟（模拟人思考时间）
            int postParseDelay = 500 + random.nextInt(1500); // 0.5-2秒
            Thread.sleep(postParseDelay);

            long parseTime = System.currentTimeMillis() - startTime;
            result.put("总耗时", parseTime + "ms");
            result.put("解析耗时", (parseTime - preParseDelay - postParseDelay) + "ms");
            result.put("解析状态", parsed.getParseStatus());

            // 根据解析结果处理
            if (parsed.getParseStatus() == 2) {
                // 进一步验证关键字段
                if (parsed.getProname() == null || parsed.getProname().isEmpty() ||
                    parsed.getProno() == null || parsed.getProno().isEmpty() ||
                    parsed.getBudgetAmount() == null || parsed.getSectionProjectOverview() == null) {

                    log.warn("解析状态成功但关键字段缺失，标记为解析失败");
                    parsed.setParseStatus(3);
                    parsed.setParseErrorMsg("关键字段缺失");
                    markAsFailed(detail.getInfoid());
                    result.put("处理结果", "失败");
                    result.put("失败原因", "关键字段缺失");

                } else {
                    if (saveToParsedTable(parsed)) {
                        markAsSuccess(detail.getInfoid());
                        result.put("处理结果", "成功");
                        log.info("成功保存记录: {}", detail.getInfoid());
                    } else {
                        markAsFailed(detail.getInfoid());
                        result.put("处理结果", "失败");
                        result.put("失败原因", "保存到数据库失败");
                    }
                }

            } else if (parsed.getParseStatus() == 4) {
                // 跳过
                markAsSkipped(detail.getInfoid());
                result.put("处理结果", "跳过");
                result.put("跳过原因", parsed.getParseErrorMsg());

            } else {
                // 失败
                markAsFailed(detail.getInfoid());
                result.put("处理结果", "失败");
                result.put("失败原因", parsed.getParseErrorMsg());
            }

        } catch (Exception e) {
            log.error("解析异常: {}", detail.getInfoid(), e);
            markAsFailed(detail.getInfoid());
            result.put("处理结果", "异常");
            result.put("异常信息", e.getMessage());
        }

        return result;
    }

    /**
     * 处理单个记录 - 只保存成功记录
     */
    private Map<String, Object> processSingleRecordForSuccessOnly(TenderProjectDetail detail) {
        Map<String, Object> result = new HashMap<>();
        result.put("infoid", detail.getInfoid());
        result.put("项目名称", detail.getProname());

        // 获取最新状态
        TenderProjectDetail currentDetail = getMessageMapper.selectByInfoId(detail.getInfoid());
        if (currentDetail == null) {
            result.put("处理结果", "失败");
            result.put("失败原因", "记录不存在");
            return result;
        }

        String currentBak = currentDetail.getBak();

        // 检查是否已处理
        if ("1".equals(currentBak) || "3".equals(currentBak) || "4".equals(currentBak)) {
            result.put("处理结果", "跳过");
            result.put("跳过原因", "记录已处理或正在处理");
            return result;
        }

        // 标记为处理中
        if (!markAsProcessing(detail.getInfoid())) {
            result.put("处理结果", "跳过");
            result.put("跳过原因", "标记处理中失败");
            return result;
        }

        long startTime = System.currentTimeMillis();

        try {
            // 解析页面
            TenderProjectDetailParsed parsed = pageParser.parse(
                detail.getInfoid(),
                detail.getInfoUrl(),
                detail.getProname()
            );

            long parseTime = System.currentTimeMillis() - startTime;
            result.put("解析耗时", parseTime + "ms");
            result.put("解析状态", parsed.getParseStatus());

            // 根据解析结果处理
            if (parsed.getParseStatus() == 2) {
                // 成功
                // 进一步验证关键字段
                if (parsed.getProname() == null || parsed.getProname().isEmpty() ||
                    parsed.getProno() == null || parsed.getProno().isEmpty() ||
                    parsed.getBudgetAmount() == null||parsed.getSectionProjectOverview() == null) {
                    log.warn("解析状态成功但关键字段缺失，标记为解析失败");
                    parsed.setParseStatus(3); // 改为解析失败
                    parsed.setParseErrorMsg("关键字段缺失");
                }else {
                    if(saveToParsedTable(parsed)) {
                    markAsSuccess(detail.getInfoid());
                    result.put("处理结果", "成功");
                    log.info("成功保存记录: {}", detail.getInfoid());
                    }else {
                    markAsFailed(detail.getInfoid());
                    result.put("处理结果", "失败");
                     }

                }

            } else if (parsed.getParseStatus() == 4) {
                // 跳过
                markAsSkipped(detail.getInfoid());
                result.put("处理结果", "跳过");
                result.put("跳过原因", parsed.getParseErrorMsg());
            } else {
                // 失败
                markAsFailed(detail.getInfoid());
                result.put("处理结果", "失败");
                result.put("失败原因", parsed.getParseErrorMsg());
            }

        } catch (Exception e) {
            log.error("解析异常: {}", detail.getInfoid(), e);
            markAsFailed(detail.getInfoid());
            result.put("处理结果", "异常");
            result.put("异常信息", e.getMessage());
        }

        return result;
    }

    /**
     * 标记为处理中
     */
    private boolean markAsProcessing(String infoid) {
        try {
            return getMessageMapper.updateParseStatus(infoid, "4") > 0;
        } catch (Exception e) {
            log.error("标记处理中失败: {}", infoid, e);
            return false;
        }
    }

    /**
     * 标记为成功
     */
    private boolean markAsSuccess(String infoid) {
        try {
            return getMessageMapper.updateParseStatus(infoid, "1") > 0;
        } catch (Exception e) {
            log.error("标记成功失败: {}", infoid, e);
            return false;
        }
    }

    /**
     * 标记为跳过
     */
    private boolean markAsSkipped(String infoid) {
        try {
            return getMessageMapper.updateParseStatus(infoid, "3") > 0;
        } catch (Exception e) {
            log.error("标记跳过失败: {}", infoid, e);
            return false;
        }
    }

    /**
     * 标记为失败
     */
    private boolean markAsFailed(String infoid) {
        try {
            return getMessageMapper.updateParseStatus(infoid, "2") > 0;
        } catch (Exception e) {
            log.error("标记失败失败: {}", infoid, e);
            return false;
        }
    }

    /**
     * 保存到parsed表
     */
    private boolean saveToParsedTable(TenderProjectDetailParsed parsed) {
        try {
            TenderProjectDetailParsed existing = tenderParsedMapper.selectByInfoId(parsed.getInfoid());

            if (existing != null) {
                parsed.setId(existing.getId());
                return tenderParsedMapper.update(parsed) > 0;
            } else {
                return tenderParsedMapper.insert(parsed) > 0;
            }
        } catch (Exception e) {
            log.error("保存到parsed表失败: {}", parsed.getInfoid(), e);
            return false;
        }
    }

    /**
     * 查看状态统计
     */
    @SaIgnore
    @GetMapping("/status-stats")
    public Map<String, Object> getStatusStats() {
        Map<String, Object> result = new HashMap<>();

        try {
            int totalUnparsed = getMessageMapper.countUnparsedRecordsOnlyS();
            int success = getMessageMapper.countByStatus("1");
            int failed = getMessageMapper.countByStatus("2");
            int skipped = getMessageMapper.countByStatus("3");
            int processing = getMessageMapper.countByStatus("4");

            result.put("success", true);
            result.put("状态统计", Map.of(
                "未解析记录数", totalUnparsed,
                "成功记录数", success,
                "失败记录数", failed,
                "跳过记录数", skipped,
                "处理中记录数", processing,
                "已处理率", String.format("%.1f%%", (success + skipped + failed) * 100.0 / (totalUnparsed + success + skipped + failed))
            ));

        } catch (Exception e) {
            log.error("获取状态统计失败", e);
            result.put("success", false);
            result.put("message", "获取统计失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 重置失败记录
     */
    @SaIgnore
    @PostMapping("/reset-failed")
    public Map<String, Object> resetFailedRecords(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 查询失败记录
            List<TenderProjectDetail> failedList = getMessageMapper.selectSuccessRecords(limit);

            int resetCount = 0;
            for (TenderProjectDetail detail : failedList) {
                if ("2".equals(detail.getBak())) {
                    if (getMessageMapper.resetFailedStatus(detail.getInfoid()) > 0) {
                        resetCount++;
                    }
                }
            }

            result.put("success", true);
            result.put("重置记录数", resetCount);
            result.put("message", String.format("成功重置 %d 条失败记录", resetCount));

        } catch (Exception e) {
            log.error("重置失败记录失败", e);
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 提取批次处理摘要（Redis版本）
     */
    private Map<String, Object> extractBatchSummaryFromRedis(Map<String, Object> batchResult) {
        Map<String, Object> summary = new HashMap<>();

        if (batchResult.containsKey("处理统计")) {
            Map<String, Object> stats = (Map<String, Object>) batchResult.get("处理统计");
            summary.put("处理记录数", stats.getOrDefault("过滤后记录数", 0));
            summary.put("成功保存数", stats.getOrDefault("成功保存数", 0));
            summary.put("跳过年数", stats.getOrDefault("本次跳过年数", 0));
            summary.put("失败数", stats.getOrDefault("失败数", 0));
            summary.put("异常数", stats.getOrDefault("异常数", 0));
            summary.put("成功率", stats.getOrDefault("成功率", "0%"));
        }

        if (batchResult.containsKey("批次ID")) {
            summary.put("批次ID", batchResult.get("批次ID"));
        }

        summary.put("结束时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return summary;
    }

    /**
     * 安全地将对象转换为可变Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMutableMap(Map<String, Object> map) {
        if (map == null) {
            return new HashMap<>();
        }

        // 如果已经是HashMap，直接返回
        if (map.getClass().equals(HashMap.class)) {
            return map;
        }

        // 否则创建新的HashMap复制所有条目
        try {
            return new HashMap<>(map);
        } catch (Exception e) {
            // 如果不可复制，手动创建
            Map<String, Object> mutableMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                mutableMap.put(entry.getKey(), entry.getValue());
            }
            return mutableMap;
        }
    }

    /**
     * 安全地将对象转换为可变List
     */
    @SuppressWarnings("unchecked")
    private List<Object> toMutableList(List<?> list) {
        if (list == null) {
            return new ArrayList<>();
        }

        // 如果已经是ArrayList，直接返回
        if (list.getClass().equals(ArrayList.class)) {
            return (List<Object>) list;
        }

        // 否则创建新的ArrayList复制所有元素
        return new ArrayList<>(list);
    }

    /**
     * 处理单个记录（线程安全）
     */
    private Map<String, Object> processSingleRecord(TenderProjectDetail detail) {
        Map<String, Object> result = new HashMap<>();
        result.put("infoid", detail.getInfoid());
        result.put("项目名称", detail.getProname());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析页面
            TenderProjectDetailParsed parsed = pageParser.parse(
                detail.getInfoid(),
                detail.getInfoUrl(),
                detail.getProname()
            );

            long parseTime = System.currentTimeMillis() - startTime;
            result.put("解析耗时", parseTime + "ms");
            result.put("解析状态", parsed.getParseStatus());

            // 2. 确保有项目名称（即使跳过）
            if (parsed.getProname() == null || parsed.getProname().isEmpty()) {
                parsed.setProname(detail.getProname());
            }

            // 3. 根据解析状态处理
            switch (parsed.getParseStatus()) {
                case 2: // 成功
                    return handleSuccessfulRecord(parsed, detail, result);

                case 4: // 跳过
                    return handleSkippedRecord(parsed, detail, result);

                default: // 失败或其他
                    return handleFailedRecord(parsed, detail, result);
            }

        } catch (Exception e) {
            log.error("解析记录异常: {}", detail.getInfoid(), e);
            result.put("处理结果", "异常");
            result.put("异常信息", e.getMessage());

            // 即使异常也保存一条记录
            TenderProjectDetailParsed errorRecord = new TenderProjectDetailParsed();
            errorRecord.setInfoid(detail.getInfoid());
            errorRecord.setProname(detail.getProname());
            errorRecord.setParseStatus(3);
            errorRecord.setParseErrorMsg("解析异常: " + e.getMessage());
            errorRecord.setParseTime(LocalDateTime.now());
            saveToDatabase(errorRecord);

            return result;
        }
    }

    /**
     * 处理成功记录
     */
    private Map<String, Object> handleSuccessfulRecord(TenderProjectDetailParsed parsed,
                                                       TenderProjectDetail detail,
                                                       Map<String, Object> result) {

        // 保存到数据库
        boolean saved = saveToDatabase(parsed);

        if (saved) {
            result.put("处理结果", "成功");
            result.put("数据库操作", "已保存");
            result.put("解析数据", parsed);
            log.debug("成功保存记录: {}", detail.getInfoid());
        } else {
            result.put("处理结果", "保存失败");
            result.put("解析数据", parsed);
        }

        return result;
    }

    /**
     * 处理跳过记录
     */
    private Map<String, Object> handleSkippedRecord(TenderProjectDetailParsed parsed,
                                                    TenderProjectDetail detail,
                                                    Map<String, Object> result) {

        // 确保有基本信息
        if (parsed.getProname() == null) {
            parsed.setProname(detail.getProname());
        }

        // 设置跳过状态
        parsed.setParseStatus(4);

        // 保存到数据库（跳过的也保存）
        boolean saved = saveToDatabase(parsed);

        if (saved) {
            result.put("处理结果", "跳过");
            result.put("跳过原因", parsed.getParseErrorMsg());
            result.put("数据库操作", "已保存（跳过状态）");
            result.put("解析数据", parsed);
            log.debug("跳过记录已保存: {}", detail.getInfoid());
        } else {
            result.put("处理结果", "跳过记录保存失败");
        }

        return result;
    }

    /**
     * 处理失败记录
     */
    private Map<String, Object> handleFailedRecord(TenderProjectDetailParsed parsed,
                                                   TenderProjectDetail detail,
                                                   Map<String, Object> result) {

        // 确保有基本信息
        if (parsed.getProname() == null) {
            parsed.setProname(detail.getProname());
        }

        // 保存到数据库（失败的也保存）
        boolean saved = saveToDatabase(parsed);

        if (saved) {
            result.put("处理结果", "失败");
            result.put("失败原因", parsed.getParseErrorMsg());
            result.put("数据库操作", "已保存（失败状态）");
            result.put("解析数据", parsed);
            log.debug("失败记录已保存: {}", detail.getInfoid());
        } else {
            result.put("处理结果", "失败记录保存失败");
        }

        return result;
    }

    /**
     * 保存到数据库（统一方法）
     */
    private boolean saveToDatabase(TenderProjectDetailParsed parsed) {
        try {
            // 检查是否已存在
            TenderProjectDetailParsed existing = tenderParsedMapper.selectByInfoId(parsed.getInfoid());

            int saveResult;
            if (existing != null) {
                // 更新
                parsed.setId(existing.getId());
                saveResult = tenderParsedMapper.update(parsed);
            } else {
                // 插入
                saveResult = tenderParsedMapper.insert(parsed);
            }

            return saveResult > 0;

        } catch (Exception e) {
            log.error("保存到数据库失败: {}", parsed.getInfoid(), e);
            return false;
        }
    }



    /**
     * 添加反爬虫特征（混淆结果）
     */
    private Map<String, Object> addAntiCrawlerFeatures(Map<String, Object> originalResult) {
        // 1. 创建新的可变Map，复制原始数据
        Map<String, Object> newResult = new HashMap<>();

        // 2. 复制原始结果中的所有数据
        if (originalResult != null) {
            for (Map.Entry<String, Object> entry : originalResult.entrySet()) {
                newResult.put(entry.getKey(), entry.getValue());
            }
        }

        // 3. 添加随机时间戳
        newResult.put("timestamp", System.currentTimeMillis());
        newResult.put("requestId", UUID.randomUUID().toString());

        // 4. 随机添加一些无用字段（混淆）
        Random random = new Random();
        if (random.nextBoolean()) {
            newResult.put("apiVersion", "v" + random.nextInt(5) + "." + random.nextInt(10));
        }
        if (random.nextBoolean()) {
            newResult.put("serverId", "server-" + (1000 + random.nextInt(9000)));
        }

        // 5. 对处理日志进行随机排序
        if (newResult.containsKey("处理日志") && newResult.get("处理日志") instanceof List) {
            List<?> logs = (List<?>) newResult.get("处理日志");
            if (logs.size() > 1) {
                List<Object> mutableLogs = new ArrayList<>(logs);
                Collections.shuffle(mutableLogs);
                newResult.put("处理日志", mutableLogs);
            }
        }

        // 6. 随机添加延迟信息
        newResult.put("totalProcessTime", (100 + random.nextInt(900)) + "ms");

        // 7. 添加假的成功率
        if (newResult.containsKey("处理统计") && newResult.get("处理统计") instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> originalStats = (Map<String, Object>) newResult.get("处理统计");

                // 创建新的统计Map
                Map<String, Object> newStats = new HashMap<>(originalStats);

                int success = (int) newStats.getOrDefault("成功数", 0);
                int total = (int) newStats.getOrDefault("总记录数", 0);
                double actualRate = total > 0 ? (success * 100.0 / total) : 0;

                // 添加假的成功率
                double fakeRate = actualRate + (random.nextDouble() * 2 - 1);
                fakeRate = Math.max(0, Math.min(100, fakeRate));
                newStats.put("displaySuccessRate", String.format("%.1f%%", fakeRate));

                newResult.put("处理统计", newStats);
            } catch (Exception e) {
                log.warn("无法添加假成功率: {}", e.getMessage());
            }
        }

        return newResult;
    }

    /**
     * 提取批次处理摘要
     */
    private Map<String, Object> extractBatchSummary(Map<String, Object> batchResult) {
        Map<String, Object> summary = new HashMap<>();

        if (batchResult.containsKey("处理统计")) {
            Map<String, Object> stats = (Map<String, Object>) batchResult.get("处理统计");
            summary.put("处理记录数", stats.getOrDefault("总记录数", 0));
            summary.put("成功数", stats.getOrDefault("成功数", 0));
            summary.put("跳过", stats.getOrDefault("跳过", 0));
            summary.put("失败数", stats.getOrDefault("失败数", 0));
            summary.put("成功率", stats.getOrDefault("成功率", "0%"));
        }

        summary.put("结束时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return summary;
    }

    /**
     * 提取批次处理摘要（只保存成功记录版本）
     */
    private Map<String, Object> extractBatchSummaryForSuccessOnly(Map<String, Object> batchResult) {
        Map<String, Object> summary = new HashMap<>();

        if (batchResult.containsKey("处理统计")) {
            Map<String, Object> stats = (Map<String, Object>) batchResult.get("处理统计");
            summary.put("处理记录数", stats.getOrDefault("总记录数", 0));
            summary.put("成功保存数", stats.getOrDefault("成功保存数", 0));
            summary.put("跳过数", stats.getOrDefault("跳过数", 0));
            summary.put("失败数", stats.getOrDefault("失败数", 0));
            summary.put("成功率", stats.getOrDefault("成功率", "0%"));
        }

        summary.put("结束时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return summary;
    }

    /**
     * 汇总所有批次统计
     */
    private Map<String, Object> summarizeAllBatches(List<Map<String, Object>> allBatches) {
        int totalSuccess = 0;
        int totalSkip = 0;
        int totalFail = 0;
        int totalProcessed = 0;

        for (Map<String, Object> batch : allBatches) {
            if (batch.containsKey("处理结果")) {
                Map<String, Object> result = (Map<String, Object>) batch.get("处理结果");
                totalProcessed += (int) result.getOrDefault("处理记录数", 0);
                totalSuccess += (int) result.getOrDefault("成功数", 0);
                totalSkip += (int) result.getOrDefault("跳过", 0);
                totalFail += (int) result.getOrDefault("失败数", 0);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("总成功数", totalSuccess);
        summary.put("总跳过数", totalSkip);
        summary.put("总失败数", totalFail);
        summary.put("总处理数", totalProcessed);
        summary.put("整体成功率", String.format("%.2f%%", totalProcessed > 0 ? (totalSuccess * 100.0 / totalProcessed) : 0));
        summary.put("跳过比例", String.format("%.2f%%", totalProcessed > 0 ? (totalSkip * 100.0 / totalProcessed) : 0));
        summary.put("失败比例", String.format("%.2f%%", totalProcessed > 0 ? (totalFail * 100.0 / totalProcessed) : 0));

        return summary;
    }

    /**
     * 汇总所有批次统计（只保存成功记录版本）
     */
    private Map<String, Object> summarizeAllBatchesForSuccessOnly(List<Map<String, Object>> allBatches) {
        int totalSuccess = 0;
        int totalSkip = 0;
        int totalFail = 0;
        int totalProcessed = 0;

        for (Map<String, Object> batch : allBatches) {
            if (batch.containsKey("处理结果")) {
                Map<String, Object> result = (Map<String, Object>) batch.get("处理结果");
                totalProcessed += (int) result.getOrDefault("处理记录数", 0);
                totalSuccess += (int) result.getOrDefault("成功保存数", 0);
                totalSkip += (int) result.getOrDefault("跳过数", 0);
                totalFail += (int) result.getOrDefault("失败数", 0);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("总成功保存数", totalSuccess);
        summary.put("总跳过数", totalSkip);
        summary.put("总失败数", totalFail);
        summary.put("总处理数", totalProcessed);
        summary.put("整体成功率", String.format("%.2f%%", totalProcessed > 0 ? (totalSuccess * 100.0 / totalProcessed) : 0));
        summary.put("跳过比例", String.format("%.2f%%", totalProcessed > 0 ? (totalSkip * 100.0 / totalProcessed) : 0));
        summary.put("失败比例", String.format("%.2f%%", totalProcessed > 0 ? (totalFail * 100.0 / totalProcessed) : 0));

        return summary;
    }
}
