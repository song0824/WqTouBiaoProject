package org.dromara.toubiao.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.dromara.toubiao.auth.HebeiTokenCommonManager;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class HebeiInfoUrlCommonClient {

    private static final String API_URL = "http://ssl.hebpr.cn/EWB-FRONT/rest/todayDeal/pageredirectnew";


    /**
     * 传入 infoId，返回完整 URL
     */
    public String getFullInfoUrl(String infoId) {
        String relativeUrl = getInfoUrl(infoId);
        if (relativeUrl == null || relativeUrl.isEmpty()) {
            return null;
        }

        // 自动处理前缀：如果返回的是以 /jydt 开头的
        // 平台规范：Base(http://ssl.hebpr.cn) + 项目名(/hbggfwpt) + 接口返回的相对路径(/jydt/...)
        if (relativeUrl.startsWith("/")) {
            return "http://ssl.hebpr.cn/hbggfwpt" + relativeUrl;
        }
        return "http://ssl.hebpr.cn/hbggfwpt/" + relativeUrl;
    }

    /**
     * 获取相对路径的核心逻辑
     */
    public String getInfoUrl(String infoId) {
        return getInfoUrlWithRetry(infoId, 1);
    }

    private String getInfoUrlWithRetry(String infoId, int retryCount) {
        CloseableHttpClient client = HebeiHttpClientCommonFactory.getClient();
        try {
            HttpPost post = buildRequest(infoId);

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();

                String json = (response.getEntity() != null) ?
                        EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";

                // 检查是否返回空报文
                if (json == null || json.trim().isEmpty()) {
                    log.error("接口返回内容为空，可能缺少 Referer 或 Token 无效");
                    log.error("当前使用的Token: {}", HebeiTokenCommonManager.getToken());
                    HebeiHttpClientCommonFactory.logCookies();
                    return null;
                }

                // 检查Token是否失效
                if (json.contains("expired_token") || json.contains("Missing authorization") || statusCode == 401) {
                    if (retryCount > 0) {
                        log.warn("Token失效，尝试强制刷新并重试一次...");
                        HebeiTokenCommonManager.forceRefresh();
                        return getInfoUrlWithRetry(infoId, retryCount - 1);
                    }
                    return null;
                }

                return parseInfoUrl(json);
            }
        } catch (Exception e) {
            log.error("获取 infoUrl 异常", e);
            return null;
        }
    }

    /**
     * 构建 POST请求
     */
    private HttpPost buildRequest(String infoId) {
        HttpPost post = new HttpPost(API_URL);

        // 1. 完整的请求头（严格按照浏览器顺序）
        post.setHeader("Accept", "application/json, text/javascript, */*; q=0.01");
        post.setHeader("Accept-Encoding", "gzip, deflate");
        post.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

        // 2. Authorization
        String token = HebeiTokenCommonManager.getToken();
        post.setHeader("Authorization", "Bearer " + token);

        post.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        // 3. Cookie
        String refreshToken = HebeiTokenCommonManager.getRefreshToken();
        StringBuilder cookieBuilder = new StringBuilder();
        cookieBuilder.append("oauthClientId=demoClient; ");
        cookieBuilder.append("oauthPath=http://172.19.3.38:8080/EpointWebBuilderZw; ");
        cookieBuilder.append("oauthLoginUrl=http://172.19.3.38:8080/EpointWebBuilderZw/rest/oauth2/authorize?client_id=demoClient&state=a&response_type=code&scope=user&redirect_uri=; ");
        cookieBuilder.append("oauthLogoutUrl=http://172.19.3.38:8080/EpointWebBuilderZw/rest/oauth2/logout?redirect_uri=; ");
        if (refreshToken != null && !refreshToken.isEmpty()) {
            cookieBuilder.append("noOauthRefreshToken=").append(refreshToken).append("; ");
        }
        if (token != null && !token.isEmpty()) {
            cookieBuilder.append("noOauthAccessToken=").append(token);
        }
        post.setHeader("Cookie", cookieBuilder.toString());

        // 4. 其他标准头
        post.setHeader("Host", "ssl.hebpr.cn");
        post.setHeader("Origin", "http://ssl.hebpr.cn");
        post.setHeader("Referer", "http://ssl.hebpr.cn/hbggfwpt/jydt/salesPlat.html");
        post.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        post.setHeader("X-Requested-With", "XMLHttpRequest");

        try {
            // 构建 JSON 对象
            JSONObject body = new JSONObject();
            body.put("infoid", infoId);
            body.put("siteGuid", "7eb5f7f1-9041-43ad-8e13-8fcb82ea831a");

            // 将 JSON 作为 form 参数发送
            List<BasicNameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("params", body.toJSONString()));

            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, StandardCharsets.UTF_8);
            post.setEntity(entity);

//            log.info("使用Token: {}", token);
//            log.info("请求体(form格式): params={}", body.toJSONString());

        } catch (Exception e) {
            log.error("构建请求体失败", e);
        }

        return post;
    }

    /**
     * 解析返回 JSON
     */
    private String parseInfoUrl(String json) {
        if (json == null || json.trim().isEmpty()) {
            log.error("接口返回的 JSON 字符串为空");
            return null;
        }

        try {
            JSONObject root = JSON.parseObject(json);
            if (root == null) {
                log.error("JSON 解析失败，root 为 null。原始报文: {}", json);
                return null;
            }

            //直接从根对象获取 infoUrl（最常见的情况）
            String infoUrl = root.getString("infoUrl");
            if (infoUrl != null && !infoUrl.isEmpty()) {
//                log.info("成功从根对象获取 infoUrl: {}", infoUrl);
                return infoUrl;
            }
        } catch (Exception e) {
            log.error("解析 JSON 报文异常: {}", e.getMessage());
        }
        return null;
    }

}
