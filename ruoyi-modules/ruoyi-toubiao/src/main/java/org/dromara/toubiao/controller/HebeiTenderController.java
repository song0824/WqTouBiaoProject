package org.dromara.toubiao.controller;

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
 */
@RestController
@RequestMapping("/hebei/tender")
@Slf4j
public class HebeiTenderController {

    @Autowired
    private HebeiTenderService hebeiTenderService;

    @Autowired
    private HebeiTenderServiceImpl hebeiTenderServiceImpl;

    /**
     * 测试系统连接和 Token 获取
     */
    @GetMapping("/test/connection")
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 强制刷新 Token
            HebeiTokenCommonManager.forceRefresh();

            String accessToken = HebeiTokenCommonManager.getToken();
            String refreshToken = HebeiTokenCommonManager.getRefreshToken();

            result.put("success", true);
            result.put("accessToken", accessToken != null ?
                    accessToken.substring(0, Math.min(30, accessToken.length())) + "..." : null);
            result.put("refreshToken", refreshToken != null ?
                    refreshToken.substring(0, Math.min(30, refreshToken.length())) + "..." : null);
            result.put("message", "连接测试成功");

            HebeiHttpClientCommonFactory.logCookies();

        } catch (Exception e) {
            log.error("连接测试失败", e);
            result.put("success", false);
            result.put("message", "连接测试失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 测试单个 infoId 的 URL 获取
     */
    @GetMapping("/test/single")
    public Map<String, Object> testSingleInfoId(@RequestParam String infoId) {
        Map<String, Object> result = new HashMap<>();

        try {
            String url = hebeiTenderService.getInfoUrl(infoId);

            result.put("success", url != null);
            result.put("infoId", infoId);
            result.put("infoUrl", url);
            result.put("message", url != null ? "获取成功" : "获取失败");

        } catch (Exception e) {
            log.error("测试失败", e);
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 批量同步所有缺失的 infoUrl
     */
    @PostMapping("/sync/all")
    public Map<String, Object> syncAllMissingUrls() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("开始批量同步任务");

            // 异步执行,避免超时
            new Thread(() -> {
                try {
                    hebeiTenderServiceImpl.updateMissingInfoUrls();
                } catch (Exception e) {
                    log.error("批量同步异常", e);
                }
            }).start();

            result.put("success", true);
            result.put("message", "批量同步任务已启动,请查看日志获取进度");

        } catch (Exception e) {
            log.error("启动同步任务失败", e);
            result.put("success", false);
            result.put("message", "启动失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 强制刷新 Token
     */
    @PostMapping("/token/refresh")
    public Map<String, Object> refreshToken() {
        Map<String, Object> result = new HashMap<>();

        try {
            HebeiTokenCommonManager.forceRefresh();

            String accessToken = HebeiTokenCommonManager.getToken();
            String refreshToken = HebeiTokenCommonManager.getRefreshToken();

            result.put("success", true);
            result.put("accessToken", accessToken != null ?
                    accessToken.substring(0, Math.min(30, accessToken.length())) + "..." : null);
            result.put("refreshToken", refreshToken != null ?
                    refreshToken.substring(0, Math.min(30, refreshToken.length())) + "..." : null);
            result.put("message", "Token 刷新成功");

        } catch (Exception e) {
            log.error("刷新 Token 失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 清空 Token 和 Cookie
     */
    @PostMapping("/token/clear")
    public Map<String, Object> clearToken() {
        Map<String, Object> result = new HashMap<>();

        try {

            HebeiTokenCommonManager.clearTokens();
            HebeiHttpClientCommonFactory.clearCookies();

            result.put("success", true);
            result.put("message", "已清空所有 Token 和 Cookie");

        } catch (Exception e) {
            log.error("清空失败", e);
            result.put("success", false);
            result.put("message", "清空失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 查看当前 Cookie 信息
     */
    @GetMapping("/cookies")
    public Map<String, Object> viewCookies() {
        Map<String, Object> result = new HashMap<>();

        try {
            HebeiHttpClientCommonFactory.logCookies();

            int cookieCount = HebeiHttpClientCommonFactory.getCookieStore().getCookies().size();

            result.put("success", true);
            result.put("cookieCount", cookieCount);
            result.put("message", "请查看日志获取详细 Cookie 信息");

        } catch (Exception e) {
            log.error("查看 Cookie 失败", e);
            result.put("success", false);
            result.put("message", "查看失败: " + e.getMessage());
        }

        return result;
    }
}
