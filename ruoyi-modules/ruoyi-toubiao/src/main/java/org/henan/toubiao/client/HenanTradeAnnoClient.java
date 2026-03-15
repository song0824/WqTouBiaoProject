package org.henan.toubiao.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.henan.toubiao.domain.HenanTradeAnno;
import org.henan.toubiao.domain.HenanTradeAnnoItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 河南省公共资源交易平台 - 公告列表接口客户端
 * POST /ggzyjy-portal/v1/tradeAnno/tradeList，参数 projectType=2 获取采购公告
 * 页面无登录/cookie，接口通过 Origin/Referer 校验“来自门户站”；须与浏览器一致用门户站根地址作为 Origin。
 */
@Slf4j
@Component
public class HenanTradeAnnoClient {

    /** 门户站根地址（浏览器里页面的地址），Origin/Referer 必须与此一致，否则接口报“缺少认证” */
    private static final String PORTAL_ORIGIN = "https://ggzy.fgw.henan.gov.cn";
    private static final String DEFAULT_BASE_URL = "https://ggzy.fgw.henan.gov.cn:19100";
    private static final String TRADE_LIST_PATH = "/ggzyjy-portal/v1/tradeAnno/tradeList";
    private static final String INFO_DETAIL_PATH = "/tradeInformationDetail/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    /** 接口根地址（可带 :19100），若 19100 不可达可改为 https://ggzy.fgw.henan.gov.cn */
    @Value("${henan.trade-anno.base-url:https://ggzy.fgw.henan.gov.cn:19100}")
    private String baseUrl;
    @Value("${henan.trade-anno.authorization:}")
    private String authorization;
    /** 每页条数，建议 20～50 减少请求次数 */
    @Value("${henan.trade-anno.page-size:20}")
    private int pageSize;
    /** 最多拉取页数，防止长时间运行或超时 */
    @Value("${henan.trade-anno.max-pages:50}")
    private int maxPages;
    /** 单次请求连接超时(毫秒) */
    @Value("${henan.trade-anno.connect-timeout:10000}")
    private int connectTimeout;
    /** 单次请求读取超时(毫秒) */
    @Value("${henan.trade-anno.read-timeout:20000}")
    private int readTimeout;
    /** 可选关键词，与接口 keyWord 一致 */
    @Value("${henan.trade-anno.key-word:}")
    private String keyWord;
    /** 并行请求页数，建议 3～6，过大可能被限流 */
    @Value("${henan.trade-anno.concurrency:4}")
    private int concurrency;

    private static final ExecutorService PAGINATION_EXECUTOR = Executors.newFixedThreadPool(6);

    public HenanTradeAnnoClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 请求公告列表（仅返回当天 createTime 的数据，并填充 infoUrl），自动翻页直到无更多数据
     */
    public List<HenanTradeAnno> fetchTodayTradeAnnoList() {
        List<HenanTradeAnnoItem> rawList = requestTradeListAllPages();
        String today = LocalDate.now().format(DATE_FMT);
        List<HenanTradeAnno> result = new ArrayList<>();
        for (HenanTradeAnnoItem item : rawList) {
            if (item == null || item.getCreateTime() == null) continue;
            String createTime = item.getCreateTime();
            if (createTime.length() >= 10 && createTime.startsWith(today)) {
                HenanTradeAnno entity = toEntity(item);
                entity.setInfoUrl(buildInfoUrl(item.getId()));
                result.add(entity);
            }
        }
        log.info("河南省公告：共拉取 {} 条，当天({}) 共 {} 条", rawList.size(), today, result.size());
        return result;
    }

    /**
     * 分页请求全部列表，多页并行以缩短总耗时，直到无更多数据或达到 maxPages
     */
    private List<HenanTradeAnnoItem> requestTradeListAllPages() {
        List<HenanTradeAnnoItem> all = new ArrayList<>();
        int size = pageSize > 0 ? pageSize : 20;
        int max = maxPages > 0 ? maxPages : 50;
        int concurr = concurrency > 0 ? Math.min(concurrency, 8) : 4;
        int nextPage = 1;
        while (nextPage <= max) {
            int from = nextPage;
            int to = Math.min(nextPage + concurr - 1, max);
            List<CompletableFuture<PageResult>> futures = new ArrayList<>();
            for (int p = from; p <= to; p++) {
                int pageNum = p;
                futures.add(CompletableFuture.supplyAsync(
                    () -> new PageResult(pageNum, requestTradeListPage(pageNum, size)),
                    PAGINATION_EXECUTOR));
            }
            List<PageResult> batch = futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(PageResult::pageNum))
                .toList();
            boolean lastBatch = false;
            for (PageResult pr : batch) {
                if (pr.list() == null || pr.list().isEmpty()) {
                    lastBatch = true;
                    break;
                }
                all.addAll(pr.list());
                if (pr.list().size() < size) {
                    lastBatch = true;
                    break;
                }
            }
            if (lastBatch) break;
            nextPage = to + 1;
        }
        if (nextPage > max && !all.isEmpty()) {
            log.warn("河南省公告：已达最大页数 {}，停止拉取", max);
        }
        return all;
    }

    private record PageResult(int pageNum, List<HenanTradeAnnoItem> list) {}

    private String buildInfoUrl(String id) {
        if (id == null || id.isEmpty()) return "";
        return PORTAL_ORIGIN + INFO_DETAIL_PATH + id.trim();
    }

    private HenanTradeAnno toEntity(HenanTradeAnnoItem item) {
        HenanTradeAnno e = new HenanTradeAnno();
        e.setId(item.getId());
        e.setAnnoType(item.getAnnoType());
        e.setTitle(item.getTitle());
        e.setContent(item.getContent());
        e.setTradeRegion(item.getTradeRegion());
        e.setTradeRegionCode(item.getTradeRegionCode());
        e.setProjectName(item.getProjectName());
        e.setProjectType(item.getProjectType());
        e.setProjectCode(item.getProjectCode());
        e.setCreateTime(item.getCreateTime());
        return e;
    }

    /**
     * POST 请求公告列表某一页，参数 projectType=2, keyWord, pageNum, pageSize
     */
    @SuppressWarnings("unchecked")
    private List<HenanTradeAnnoItem> requestTradeListPage(int pageNum, int pageSize) {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : DEFAULT_BASE_URL;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base + TRADE_LIST_PATH;
        try {
            String body = buildRequestBody(pageNum, pageSize);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Origin", PORTAL_ORIGIN);
            conn.setRequestProperty("Referer", PORTAL_ORIGIN + "/");
            conn.setRequestProperty("Sec-Fetch-Dest", "empty");
            conn.setRequestProperty("Sec-Fetch-Mode", "cors");
            conn.setRequestProperty("Sec-Fetch-Site", "same-site");
            if (authorization != null && !authorization.isBlank()) {
                conn.setRequestProperty("Authorization", authorization.trim());
            }
            conn.setConnectTimeout(connectTimeout > 0 ? connectTimeout : 10000);
            conn.setReadTimeout(readTimeout > 0 ? readTimeout : 20000);
            conn.getOutputStream().write(bytes);

            int code = conn.getResponseCode();
            java.io.InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();

            if (code != 200) {
                log.warn("河南省公告接口异常: code={}, body={}", code, responseBody);
                return new ArrayList<>();
            }

            return parseTradeListResponse(responseBody);
        } catch (Exception e) {
            log.error("请求河南省公告列表第{}页失败: url={}", pageNum, url, e);
        }
        return new ArrayList<>();
    }

    private String buildRequestBody(int pageNum, int pageSize) {
        try {
            String kw = (keyWord != null && !keyWord.isBlank()) ? URLEncoder.encode(keyWord.trim(), StandardCharsets.UTF_8) : "";
            return "projectType=2&keyWord=" + kw + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        } catch (Exception e) {
            return "projectType=2&pageNum=" + pageNum + "&pageSize=" + pageSize;
        }
    }

    /**
     * 解析接口返回：兼容 data 为数组、或 data 为对象且内含 list/records 等
     */
    @SuppressWarnings("unchecked")
    private List<HenanTradeAnnoItem> parseTradeListResponse(String responseBody) {
        try {
            Map<String, Object> map = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            // 1) 根节点直接是数组（少见）
            Object rootList = map.get("list");
            if (rootList instanceof List) {
                return objectMapper.convertValue(rootList, new TypeReference<List<HenanTradeAnnoItem>>() {});
            }
            // 2) 根下 data/records 是数组
            for (String key : new String[] { "data", "records" }) {
                Object val = map.get(key);
                if (val instanceof List) {
                    return objectMapper.convertValue(val, new TypeReference<List<HenanTradeAnnoItem>>() {});
                }
                if (val instanceof Map) {
                    List<HenanTradeAnnoItem> inner = extractListFromMap((Map<String, Object>) val);
                    if (inner != null) return inner;
                }
            }
            // 3) data 为对象时，从对象内取 list/records/rows 等
            Object data = map.get("data");
            if (data instanceof Map) {
                List<HenanTradeAnnoItem> inner = extractListFromMap((Map<String, Object>) data);
                if (inner != null) return inner;
            }
        } catch (Exception e) {
            log.warn("解析公告列表响应失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<HenanTradeAnnoItem> extractListFromMap(Map<String, Object> obj) {
        for (String key : new String[] { "list", "records", "data", "rows", "content" }) {
            Object v = obj.get(key);
            if (v instanceof List) {
                return objectMapper.convertValue(v, new TypeReference<List<HenanTradeAnnoItem>>() {});
            }
        }
        return null;
    }
}
