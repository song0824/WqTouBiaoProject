package org.dromara.toubiao.auth;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.dromara.toubiao.client.HebeiHttpClientCommonFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 河北公共资源交易平台令牌管理器
 */
@Slf4j
public class HebeiTokenCommonManager {

    private static final String ANONYMOUS_TOKEN_URL = "http://ssl.hebpr.cn/EWB-FRONT/rest/getOauthInfoAction/getNoUserAccessToken";

    /** Access Token */
    private static volatile String accessToken;

    /** Refresh Token
     */
    @Getter
    private static volatile String refreshToken;

    /** Token 获取时间戳 */
    private static volatile long tokenTimeMillis;

    /** Access Token 有效期: 25 分钟 */
    private static final long ACCESS_TOKEN_EXPIRE_MILLIS = 25 * 60 * 1000;

    /** Refresh Token 有效期: 7 天 */
    private static final long REFRESH_TOKEN_EXPIRE_MILLIS = 7 * 24 * 60 * 60 * 1000;

    /** 线程锁 */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /**
     * 获取可用的 Access Token
     */
    public static String getToken() {
        if (isAccessTokenValid()) {
            return accessToken;
        }

        LOCK.lock();
        try {
            // 双重检查
            if (isAccessTokenValid()) {
                return accessToken;
            }

            // 如果 Refresh Token 还有效,尝试用它刷新 Access Token
            if (isRefreshTokenValid()) {
                log.info("Access Token 已过期,尝试使用 Refresh Token 刷新...");
                refreshAccessToken();
            } else {
                log.info("Refresh Token 也已过期,重新获取完整令牌...");
                refreshAllTokens();
            }

            return accessToken;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * 强制刷新所有令牌
     */
    public static void forceRefresh() {
        LOCK.lock();
        try {
            log.warn("收到强制刷新指令,准备重新获取所有令牌...");
            refreshAllTokens();
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * 检查 Access Token 是否有效
     */
    private static boolean isAccessTokenValid() {
        return accessToken != null
                && !accessToken.isEmpty()
                && (System.currentTimeMillis() - tokenTimeMillis) < ACCESS_TOKEN_EXPIRE_MILLIS;
    }

    /**
     * 检查 Refresh Token 是否有效
     */
    private static boolean isRefreshTokenValid() {
        return refreshToken != null
                && !refreshToken.isEmpty()
                && (System.currentTimeMillis() - tokenTimeMillis) < REFRESH_TOKEN_EXPIRE_MILLIS;
    }

    /**
     * 刷新 Access Token
     */
    private static void refreshAccessToken() {
        refreshAllTokens();
    }

    /**
     * 核心方法: 重新获取所有令牌
     */
    private static void refreshAllTokens() {
        try {
            CloseableHttpClient client = HebeiHttpClientCommonFactory.getClient();

            // 环境初始化
            String homeUrl = "http://ssl.hebpr.cn/hbggfwpt/jydt/salesPlat.html";
            accessPage(client, homeUrl, "环境初始化");

            // 发起 POST 请求获取 Token
            HttpPost post = new HttpPost(ANONYMOUS_TOKEN_URL);
            post.setHeader("Referer", homeUrl);
            post.setHeader("Origin", "http://ssl.hebpr.cn");
            post.setHeader("X-Requested-With", "XMLHttpRequest");
            post.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            List<BasicNameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("params", "{}"));
            post.setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = client.execute(post)) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
//                log.info("Token 接口返回内容: {}", jsonResponse);

                JSONObject jsonObject = JSON.parseObject(jsonResponse);
                JSONObject custom = (jsonObject != null) ? jsonObject.getJSONObject("custom") : null;

                if (custom != null) {
                    accessToken = custom.getString("access_token");
                    refreshToken = custom.getString("refresh_token");

                    if (accessToken != null) {
//                        log.info("成功从 JSON 响应中提取 AccessToken: {}", accessToken);
                        tokenTimeMillis = System.currentTimeMillis();

                        //将token设置到cookie中
                        setTokensToCookie();

                        HebeiHttpClientCommonFactory.logCookies();
                        return;
                    }
                }

                extractTokensFromCookie();
            }
        } catch (Exception e) {
            log.error("模拟 JS 获取 Token 异常", e);
        }
    }

    /**
     * 将token手动设置到cookie store中
     */
    private static void setTokensToCookie() {
        try {
            CookieStore cookieStore = HebeiHttpClientCommonFactory.getCookieStore();

            // 创建token cookie
            org.apache.http.impl.cookie.BasicClientCookie accessTokenCookie =
                    new org.apache.http.impl.cookie.BasicClientCookie("noOauthAccessToken", accessToken);
            accessTokenCookie.setDomain(".hebpr.cn");
            accessTokenCookie.setPath("/");
            accessTokenCookie.setSecure(false);
            cookieStore.addCookie(accessTokenCookie);
//            log.info("设置Cookie: noOauthAccessToken = {}", accessToken);

            if (refreshToken != null) {
                org.apache.http.impl.cookie.BasicClientCookie refreshTokenCookie =
                        new org.apache.http.impl.cookie.BasicClientCookie("noOauthRefreshToken", refreshToken);
                refreshTokenCookie.setDomain(".hebpr.cn");
                refreshTokenCookie.setPath("/");
                refreshTokenCookie.setSecure(false);
                cookieStore.addCookie(refreshTokenCookie);
//                log.info("设置Cookie: noOauthRefreshToken = {}", refreshToken);
            }


            String[][] otherCookies = {
                    {"oauthClientId", "demoClient"},
                    {"oauthPath", "http://172.19.3.38:8080/EpointWebBuilderZw"},
                    {"oauthLoginUrl", "http://172.19.3.38:8080/EpointWebBuilderZw/rest/oauth2/authorize?client_id=demoClient&state=a&response_type=code&scope=user&redirect_uri="},
                    {"oauthLogoutUrl", "http://172.19.3.38:8080/EpointWebBuilderZw/rest/oauth2/logout?redirect_uri="}
            };

            for (String[] cookieData : otherCookies) {
                org.apache.http.impl.cookie.BasicClientCookie cookie =
                        new org.apache.http.impl.cookie.BasicClientCookie(cookieData[0], cookieData[1]);
                cookie.setDomain(".hebpr.cn");
                cookie.setPath("/");
                cookie.setSecure(false);
                cookie.setVersion(0);
                cookieStore.addCookie(cookie);
            }

        } catch (Exception e) {
            log.error("设置cookie失败", e);
        }
    }

    /**
     * 访问指定页面
     */
    private static void accessPage(CloseableHttpClient client, String url, String pageName) {
        try {
            HttpGet get = new HttpGet(url);

            // 设置完整的浏览器请求头
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            get.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            get.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            get.setHeader("Accept-Encoding", "gzip, deflate");
            get.setHeader("Connection", "keep-alive");
            get.setHeader("Upgrade-Insecure-Requests", "1");
            get.setHeader("Cache-Control", "max-age=0");

            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
//                log.info("访问 {} - URL: {} - 状态码: {}", pageName, url, statusCode);

                // 消费响应内容
                if (response.getEntity() != null) {
                    EntityUtils.consumeQuietly(response.getEntity());
                }

                // 如果返回 404,尝试其他 URL
                if (statusCode == 404) {
                    log.warn("{} 返回 404,可能需要调整 URL", pageName);
                }
            }
        } catch (Exception e) {
            log.error("访问 {} 失败: {}", pageName, url, e);
        }
    }

    /**
     * 从 Cookie 中提取令牌
     */
    private static void extractTokensFromCookie() {
        List<Cookie> cookies = HebeiHttpClientCommonFactory.getCookieStore().getCookies();

        String tempAccessToken = null;
        String tempRefreshToken = null;


        for (Cookie cookie : cookies) {
            String name = cookie.getName();
            String value = cookie.getValue();

            switch (name) {
                case "noOauthAccessToken":
                    tempAccessToken = value;
                    log.info("找到 noOauthAccessToken");
                    break;
                case "noOauthRefreshToken":
                    tempRefreshToken = value;
                    log.info("找到 noOauthRefreshToken");
                    break;
                case "EPTOKEN":
                    log.info("找到 EPTOKEN");
                    break;
            }
        }

        // 设置 Access Token
        if (tempAccessToken != null) {
            accessToken = tempAccessToken;
        } else {
            accessToken = null;
//            log.error("未找到任何可用的 Access Token!");
        }

        // 设置 Refresh Token
        refreshToken = tempRefreshToken;

        if (refreshToken == null) {
//            log.warn("未找到 noOauthRefreshToken");
        }
    }

    /**
     * 清除所有令牌
     */
    public static void clearTokens() {
        LOCK.lock();
        try {
            accessToken = null;
            refreshToken = null;
            tokenTimeMillis = 0;
            log.info("已清除所有令牌");
        } finally {
            LOCK.unlock();
        }
    }
}
