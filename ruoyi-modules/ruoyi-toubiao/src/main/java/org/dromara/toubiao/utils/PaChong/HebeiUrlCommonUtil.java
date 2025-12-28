package org.dromara.toubiao.utils.PaChong;

public class HebeiUrlCommonUtil {

    private static final String HOST = "http://ssl.hebpr.cn/hbggfwpt";

    /**
     * 相对路径 → 完整详情页 URL
     */
    public static String buildDetailUrl(String infoUrl) {
        if (infoUrl == null || infoUrl.isEmpty()) {
            return null;
        }

        if (infoUrl.startsWith("http")) {
            return infoUrl;
        }

        if (!infoUrl.startsWith("/")) {
            infoUrl = "/" + infoUrl;
        }

        return HOST + infoUrl;
    }
}
