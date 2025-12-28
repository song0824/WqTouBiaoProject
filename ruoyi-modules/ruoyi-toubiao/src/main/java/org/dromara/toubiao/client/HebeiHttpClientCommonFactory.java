package org.dromara.toubiao.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * 河北公共资源交易平台 HTTP 客户端工厂
 * 特点:
 * 1. 单例模式,复用连接
 * 2. 自动管理 Cookie
 * 3. 连接池管理
 * 4. SSL 支持
 */
@Slf4j
public class HebeiHttpClientCommonFactory {

    /** 单例 HttpClient */
    private static volatile CloseableHttpClient httpClient;

    /**
     * 全局 Cookie 存储
     */
    @Getter
    private static final CookieStore cookieStore = new BasicCookieStore();

    /** 连接管理器 */
    private static PoolingHttpClientConnectionManager connectionManager;

    /**
     * 获取 HttpClient 实例
     */
    public static CloseableHttpClient getClient() {
        if (httpClient == null) {
            synchronized (HebeiHttpClientCommonFactory.class) {
                if (httpClient == null) {
                    httpClient = createHttpClient();
//                    log.info("HttpClient 初始化完成");
                }
            }
        }
        return httpClient;
    }

    /**
     * 创建 HttpClient
     */
    private static CloseableHttpClient createHttpClient() {
        try {
            // 1. 配置 SSL
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((chain, authType) -> true) // 信任所有证书
                    .build();

            SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
            );

            // 2. 配置连接管理器
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
                    .<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslFactory)
                    .build();

            connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connectionManager.setMaxTotal(100);
            connectionManager.setDefaultMaxPerRoute(20);

            // 3. 配置请求参数
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(30000)
                    .setSocketTimeout(60000)
                    .setConnectionRequestTimeout(10000)
                    .setCookieSpec(CookieSpecs.NETSCAPE)
                    .setRedirectsEnabled(true)
                    .setMaxRedirects(2)
                    .build();

            // 4. 构建 HttpClient
            return HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(requestConfig)
                    .setDefaultCookieStore(cookieStore)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

        } catch (Exception e) {
            log.error("创建 HttpClient 失败", e);
            throw new RuntimeException("HttpClient 初始化失败", e);
        }
    }

    /**
     * 清空 Cookie
     */
    public static void clearCookies() {
        cookieStore.clear();
        log.info("已清空所有 Cookie");
    }

    /**
     * 关闭 HttpClient
     */
    public static void shutdown() {
        if (httpClient != null) {
            try {
                httpClient.close();
                log.info("HttpClient 已关闭");
            } catch (Exception e) {
                log.error("关闭 HttpClient 失败", e);
            }
        }

        if (connectionManager != null) {
            connectionManager.close();
            log.info("连接管理器已关闭");
        }
    }

    /**
     * 打印当前的 Cookie 信息
     */
    public static void logCookies() {
        cookieStore.getCookies().forEach(cookie ->
                log.info("  {} = {} (Domain: {})",
                        cookie.getName(),
                        cookie.getValue().substring(0, Math.min(30, cookie.getValue().length())),
                        cookie.getDomain())
        );
    }


    private static final ExecutorService executorService = new ThreadPoolExecutor(
            8,
            10,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder().setNameFormat("tender-sync-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public static ExecutorService getExecutor() {
        return executorService;
    }
}
