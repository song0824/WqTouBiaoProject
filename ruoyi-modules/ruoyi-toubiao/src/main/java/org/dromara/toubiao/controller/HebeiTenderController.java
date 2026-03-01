package org.dromara.toubiao.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.extern.slf4j.Slf4j;
import org.dromara.toubiao.auth.HebeiTokenCommonManager;
import org.dromara.toubiao.client.HebeiHttpClientCommonFactory;
import org.dromara.toubiao.service.HebeiTenderService;
import org.dromara.toubiao.service.Impl.HebeiTenderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 河北招标信息控制器
 *
 * 该控制器提供河北招标信息相关的API接口，包括：
 * 1. 系统连接测试和Token管理
 * 2. 招标信息URL获取
 * 3. 批量同步任务
 * 4. Cookie信息查看
 */
@RestController
@RequestMapping("/hebei/tender")
@Slf4j
public class HebeiTenderController {

    /**
     * 河北招标服务接口，用于处理招标信息相关的业务逻辑
     */
    @Autowired
    private HebeiTenderService hebeiTenderService;

    /**
     * 河北招标服务实现类，用于执行具体的业务操作
     */
    @Autowired
    private HebeiTenderServiceImpl hebeiTenderServiceImpl;

    /**
     * 测试系统连接和Token获取
     *
     * 该接口用于测试与河北招标系统的连接状态，并获取访问令牌。
     * 执行流程：
     * 1. 强制刷新Token
     * 2. 获取新的访问令牌和刷新令牌
     * 3. 记录Cookie信息到日志
     *
     * @return 包含连接状态、Token信息和消息的Map对象
     */
    @GetMapping("/test/connection")
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 强制刷新Token，获取最新的访问凭证
            HebeiTokenCommonManager.forceRefresh();

            // 获取访问令牌和刷新令牌
            String accessToken = HebeiTokenCommonManager.getToken();
            String refreshToken = HebeiTokenCommonManager.getRefreshToken();

            // 构建返回结果，包含Token信息（只显示前30个字符）
            result.put("success", true);
            result.put("accessToken", accessToken != null ?
                    accessToken.substring(0, Math.min(30, accessToken.length())) + "..." : null);
            result.put("refreshToken", refreshToken != null ?
                    refreshToken.substring(0, Math.min(30, refreshToken.length())) + "..." : null);
            result.put("message", "连接测试成功");

            // 记录Cookie信息到日志
            HebeiHttpClientCommonFactory.logCookies();

        } catch (Exception e) {
            // 连接测试失败，记录错误日志并返回错误信息
            log.error("连接测试失败", e);
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试单个infoId的URL获取
     *
     * 该接口用于测试根据招标信息ID获取对应的URL地址。
     *
     * @param infoId 招标信息ID
     * @return 包含获取状态、infoId、infoUrl和消息的Map对象
     */
    @GetMapping("/test/single")
    public Map<String, Object> testSingleInfoId(@RequestParam String infoId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 根据infoId获取对应的URL地址
            String url = hebeiTenderService.getInfoUrl(infoId);

            // 构建返回结果
            result.put("success", url != null);
            result.put("infoId", infoId);
            result.put("infoUrl", url);
            result.put("message", url != null ? "获取成功" : "获取失败");

        } catch (Exception e) {
            // 获取失败，记录错误日志并返回错误信息
            log.error("测试失败", e);
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 批量同步所有缺失的infoUrl
     *
     * 该接口用于批量同步数据库中缺失infoUrl的招标信息记录。
     * 由于同步操作可能耗时较长，采用异步执行方式，避免HTTP请求超时。
     *
     * @return 包含任务启动状态和消息的Map对象
     */
    @SaIgnore
    @PostMapping("/sync/all")
    public Map<String, Object> syncAllMissingUrls() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 记录批量同步任务开始的日志
            log.info("开始批量同步任务");

            // 使用新线程异步执行批量同步任务，避免阻塞HTTP请求
            new Thread(() -> {
                try {
                    // 执行批量同步操作，更新缺失的infoUrl
                    hebeiTenderServiceImpl.updateMissingInfoUrls();
                } catch (Exception e) {
                    // 同步过程中出现异常，记录错误日志
                    log.error("批量同步异常", e);
                }
            }).start();

            // 返回任务启动成功的消息
            result.put("success", true);
            result.put("message", "批量同步任务已启动,请查看日志获取进度");

        } catch (Exception e) {
            // 启动同步任务失败，记录错误日志并返回错误信息
            log.error("启动同步任务失败", e);
            result.put("success", false);
            result.put("message", "启动失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 强制刷新Token
     *
     * 该接口用于强制刷新访问令牌，获取新的Token。
     *
     * @return 包含刷新状态、新Token信息和消息的Map对象
     */
    @PostMapping("/token/refresh")
    public Map<String, Object> refreshToken() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 强制刷新Token，获取最新的访问凭证
            HebeiTokenCommonManager.forceRefresh();

            // 获取新的访问令牌和刷新令牌
            String accessToken = HebeiTokenCommonManager.getToken();
            String refreshToken = HebeiTokenCommonManager.getRefreshToken();

            // 构建返回结果，包含新Token信息（只显示前30个字符）
            result.put("success", true);
            result.put("accessToken", accessToken != null ?
                    accessToken.substring(0, Math.min(30, accessToken.length())) + "..." : null);
            result.put("refreshToken", refreshToken != null ?
                    refreshToken.substring(0, Math.min(30, refreshToken.length())) + "..." : null);
            result.put("message", "Token 刷新成功");

        } catch (Exception e) {
            // Token刷新失败，记录错误日志并返回错误信息
            log.error("刷新 Token 失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 清空Token和Cookie
     *
     * 该接口用于清空系统中存储的所有Token和Cookie信息。
     *
     * @return 包含清空状态和消息的Map对象
     */
    @PostMapping("/token/clear")
    public Map<String, Object> clearToken() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 清空所有Token信息
            HebeiTokenCommonManager.clearTokens();
            // 清空所有Cookie信息
            HebeiHttpClientCommonFactory.clearCookies();

            // 返回清空成功的消息
            result.put("success", true);
            result.put("message", "已清空所有 Token 和 Cookie");

        } catch (Exception e) {
            // 清空失败，记录错误日志并返回错误信息
            log.error("清空失败", e);
            result.put("success", false);
            result.put("message", "清空失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 查看当前Cookie信息
     *
     * 该接口用于查看当前系统中存储的Cookie信息。
     * 详细的Cookie信息会被记录到日志中，接口返回Cookie的数量。
     *
     * @return 包含查看状态、Cookie数量和消息的Map对象
     */
    @GetMapping("/cookies")
    public Map<String, Object> viewCookies() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 记录Cookie信息到日志
            HebeiHttpClientCommonFactory.logCookies();

            // 获取当前Cookie的数量
            int cookieCount = HebeiHttpClientCommonFactory.getCookieStore().getCookies().size();

            // 返回Cookie数量和提示消息
            result.put("success", true);
            result.put("cookieCount", cookieCount);
            result.put("message", "请查看日志获取详细 Cookie 信息");

        } catch (Exception e) {
            // 查看Cookie失败，记录错误日志并返回错误信息
            log.error("查看 Cookie 失败", e);
            result.put("success", false);
            result.put("message", "查看失败: " + e.getMessage());
        }

        return result;
    }
}
