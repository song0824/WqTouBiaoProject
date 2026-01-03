package org.dromara.toubiao.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.dromara.toubiao.auth.HebeiTokenCommonManager;
import org.dromara.toubiao.client.HebeiHttpClientCommonFactory;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class HebeiPageParser {
    @Autowired
    private HebeiPageParserConfig config;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分"),
        DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    );

    private static final Pattern PATTERN_KEY_VALUE = Pattern.compile("^\\s*([^：:]+)[：:]\\s*(.+)$");
    private static final Pattern PATTERN_DEADLINE_IN_OVERVIEW = Pattern.compile("于\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})\\(北京时间\\)前递交");
    private static final Pattern PATTERN_PUBLISH_TIME = Pattern.compile("发布时间[：:]\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})");

    // 新增：针对非结构化页面的正则模式
    private static final Pattern PATTERN_PROJECT_NAME_GENERAL = Pattern.compile("([^招标|采购|成交|公告|公示|通知]+)(?:招标|采购|成交|公告|公示|通知)");
    private static final Pattern PATTERN_PROJECT_OVERVIEW_KEYWORD = Pattern.compile("(项目概况|项目基本情况|工程概况|采购需求|项目内容)");
    private static final Pattern PATTERN_BUDGET_GENERAL = Pattern.compile("(?:预算金额|总投资|最高投标限价|控制价|最高限价)[：:：]\\s*([\\d.,]+)\\s*(?:万元|元|万)?");

    public TenderProjectDetailParsed parse(String infoid, String infoUrl) {
        TenderProjectDetailParsed parsed = new TenderProjectDetailParsed();
        parsed.setInfoid(infoid);
        parsed.setInfoUrl(infoUrl);
        parsed.setParseTime(LocalDateTime.now());

        try {
            log.info("开始解析河北页面：{}", infoUrl);
            String html = fetchHtml(infoUrl);
            if (html == null || html.isEmpty()) {
                throw new RuntimeException("获取HTML内容失败或为空");
            }
            Document doc = Jsoup.parse(html);

            // 1. 总是先尝试提取顶部信息（发布时间和地区）
            extractInfoFromIntro(doc, parsed);

            Element ewbCopyDiv = doc.selectFirst("div.ewb-copy");
            if (ewbCopyDiv == null) {
                log.error("页面结构异常：未找到核心内容区域 div.ewb-copy, url: {}", infoUrl);
                throw new RuntimeException("页面结构不符合预期，缺少 div.ewb-copy");
            }

            // 2. 【智能判断】检查页面是否为已知的结构化类型
            if (isStructuredPage(ewbCopyDiv)) {
                log.info("检测到结构化页面，执行标准解析");
                parseEnhanced(ewbCopyDiv, parsed);
            } else {
                log.info("检测到非结构化页面，执行降级解析（仅提取核心字段）");
                parseMinimal(ewbCopyDiv, parsed);
            }

            parsed.setParseStatus(2);
            log.info("页面解析成功：{}", infoid);
        } catch (Exception e) {
            log.error("页面解析失败：{}", infoUrl, e);
            parsed.setParseStatus(3);
            parsed.setParseErrorMsg(e.getMessage());
        }
        return parsed;
    }

    /**
     * 判断页面是否为已知的结构化类型
     */
    private boolean isStructuredPage(Element ewbCopyDiv) {
        // 检查1：是否有<strong>标签
        boolean hasStrongTags = !ewbCopyDiv.select("strong").isEmpty();

        // 检查2：是否有明确的章节编号如"一、项目基本情况"
        boolean hasStructuredSections = ewbCopyDiv.text().matches("(?s).*[一二三四五六七八九十]、[^\\n]+");

        // 检查3：是否有清晰的键值对结构
        boolean hasKeyValueStructure = ewbCopyDiv.text().contains("项目编号：") ||
            ewbCopyDiv.text().contains("预算金额：");

        // 检查4：是否有<p><strong>...</strong></p>结构
        boolean hasPStrongStructure = ewbCopyDiv.html().contains("<p><strong>");

        log.debug("页面结构检测 - hasStrongTags: {}, hasStructuredSections: {}, hasKeyValueStructure: {}, hasPStrongStructure: {}",
            hasStrongTags, hasStructuredSections, hasKeyValueStructure, hasPStrongStructure);

        // 满足任一条件即认为是结构化页面
        return hasStrongTags || hasStructuredSections || hasKeyValueStructure || hasPStrongStructure;
    }

    /**
     * 降级解析：仅提取最核心的字段（用于非结构化页面）
     */
    private void parseMinimal(Element ewbCopyDiv, TenderProjectDetailParsed parsed) {
        String fullText = ewbCopyDiv.text();
        log.debug("降级解析，全文长度: {} 字符", fullText.length());

        // 1. 尝试提取项目名称（从标题或开头）
        if (parsed.getProname() == null) {
            // 方法A：查找第一个加粗或带下划线的文本（通常是标题）
            Element titleElement = ewbCopyDiv.select("u, b, strong").first();
            if (titleElement != null) {
                String titleText = titleElement.text().trim();
                // 清理常见的后缀
                titleText = titleText.replaceAll("(?:招标|采购|成交|公告|公示|通知).*$", "");
                if (titleText.length() > 2 && titleText.length() < 100) {
                    parsed.setProname(titleText);
                    log.debug("从标题元素提取项目名称: {}", titleText);
                }
            }

            // 方法B：使用正则从全文匹配
            if (parsed.getProname() == null) {
                Matcher nameMatcher = PATTERN_PROJECT_NAME_GENERAL.matcher(fullText);
                if (nameMatcher.find()) {
                    String projectName = nameMatcher.group(1).trim();
                    if (projectName.length() > 2 && projectName.length() < 100) {
                        parsed.setProname(projectName);
                        log.debug("从正则匹配提取项目名称: {}", projectName);
                    }
                }
            }
        }

        // 2. 提取项目概况（查找包含关键词的段落）
        if (parsed.getSectionProjectOverview() == null) {
            // 查找包含"项目概况"、"基本情况"等关键词的段落
            String overview = extractTextAroundKeyword(ewbCopyDiv,
                Arrays.asList("项目概况", "基本情况", "工程概况", "招标条件", "项目背景"));

            if (overview != null && overview.length() > 10) {
                parsed.setSectionProjectOverview(overview);
                log.debug("提取到项目概况，长度: {}", overview.length());
            } else {
                // 备用：取前200个字符作为概况
                String shortOverview = fullText.length() > 200 ?
                    fullText.substring(0, 200) + "..." : fullText;
                parsed.setSectionProjectOverview(shortOverview);
            }
        }

        // 3. 提取预算/金额信息
        if (parsed.getBudgetAmount() == null) {
            Matcher budgetMatcher = PATTERN_BUDGET_GENERAL.matcher(fullText);
            if (budgetMatcher.find()) {
                String budgetStr = budgetMatcher.group(1).replaceAll(",", "");
                parsed.setBudgetAmount(parseAmount(budgetStr));
                log.debug("提取到预算金额: {}", budgetStr);
            }
        }

        // 4. 尝试提取采购需求/招标范围
        if (parsed.getSectionProjectNeed() == null) {
            String projectNeed = extractTextAroundKeyword(ewbCopyDiv,
                Arrays.asList("采购需求", "招标范围", "项目内容", "建设内容", "服务内容"));

            if (projectNeed != null && projectNeed.length() > 10) {
                parsed.setSectionProjectNeed(projectNeed);
            }
        }

        log.info("降级解析完成，提取字段: 项目名称={}, 预算金额={}",
            parsed.getProname() != null, parsed.getBudgetAmount() != null);
    }

    /**
     * 提取关键词周围的文本（用于非结构化页面）
     */
    private String extractTextAroundKeyword(Element container, List<String> keywords) {
        String fullText = container.text();

        for (String keyword : keywords) {
            int index = fullText.indexOf(keyword);
            if (index >= 0) {
                // 从关键词位置开始，取后面300个字符
                int start = index + keyword.length();
                int end = Math.min(fullText.length(), start + 300);
                String extracted = fullText.substring(start, end).trim();

                // 清理提取的文本（移除多余空格和换行）
                extracted = extracted.replaceAll("\\s+", " ");

                // 尝试找到句子结束点
                int sentenceEnd = Math.max(
                    extracted.indexOf("。"),
                    Math.max(extracted.indexOf("；"), extracted.indexOf("\n"))
                );

                if (sentenceEnd > 0) {
                    extracted = extracted.substring(0, sentenceEnd + 1);
                }

                if (!extracted.isEmpty()) {
                    return keyword + "：" + extracted;
                }
            }
        }
        return null;
    }

    /**
     * 从 ewb-info-intro 中提取发布时间和地区信息
     */
    private void extractInfoFromIntro(Document doc, TenderProjectDetailParsed parsed) {
        Element introDiv = doc.selectFirst("div.ewb-info-intro");
        if (introDiv == null) {
            log.warn("未找到 ewb-info-intro 区域");
            return;
        }

        String introText = introDiv.text();

        // 1. 提取发布时间
        if (parsed.getPublishTime() == null) {
            Matcher timeMatcher = PATTERN_PUBLISH_TIME.matcher(introText);
            if (timeMatcher.find()) {
                String publishTimeStr = timeMatcher.group(1);
                parsed.setPublishTime(parseDateTime(publishTimeStr));
            }
        }

        // 2. 提取地区信息 - 使用精确的HTML选择器
        if (parsed.getArea() == null) {
            Element infoElement = doc.selectFirst("span#infod");
            if (infoElement != null) {
                String area = infoElement.text().trim();
                area = area.replaceAll("\\s+", "");
                parsed.setArea(area);
            }
            else {
                for (Element span : introDiv.select("span")) {
                    if (span.text().contains("信息来源")) {
                        String text = span.text();
                        int colonIndex = text.indexOf("：");
                        if (colonIndex > 0) {
                            String area = text.substring(colonIndex + 1).trim();
                            area = area.replaceAll("[0-9\\s].*$", "").trim();
                            if (!area.isEmpty()) {
                                parsed.setArea(area);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 3. 清理已解析的地区信息
        if (parsed.getArea() != null) {
            String cleanedArea = parsed.getArea()
                .replaceAll("阅读次数.*", "")
                .replaceAll("\\s+", "")
                .replaceAll("[:：]", "")
                .trim();

            if (!cleanedArea.isEmpty() && !cleanedArea.equals(parsed.getArea())) {
                parsed.setArea(cleanedArea);
            }
        }

        // 4. 如果没有找到地区信息，尝试从项目名称中推断
        if ((parsed.getArea() == null || parsed.getArea().isEmpty()) && parsed.getProname() != null) {
            inferAreaFromProjectName(parsed);
        }
    }

    /**
     * 从项目名称中推断地区
     */
    private void inferAreaFromProjectName(TenderProjectDetailParsed parsed) {
        String projectName = parsed.getProname();
        if (projectName == null) return;

        String[] areaKeywords = {
            "邯郸", "石家庄", "保定", "唐山", "邢台", "秦皇岛",
            "张家口", "承德", "沧州", "廊坊", "衡水", "定州", "辛集", "大厂"
        };

        for (String keyword : areaKeywords) {
            if (projectName.contains(keyword)) {
                parsed.setArea(keyword.contains("厂") ? "大厂回族自治县" : keyword + "市");
                break;
            }
        }
    }

    /**
     * 增强版解析方法，直接按HTML结构解析
     */
    private void parseEnhanced(Element ewbCopyDiv, TenderProjectDetailParsed parsed) {
        Map<String, String> sections = extractSections(ewbCopyDiv);

        log.info("提取到 {} 个章节: {}", sections.size(), sections.keySet());

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            log.debug("处理章节: {} -> 长度: {}", entry.getKey(), entry.getValue().length());
            processSectionEnhanced(entry.getKey(), entry.getValue(), parsed);
        }

        if (parsed.getBiddingDeadline() == null && sections.containsKey("项目概况")) {
            extractDeadlineFromOverview(sections.get("项目概况"), parsed);
        }

        // 如果关键字段未提取到，尝试全局兜底
        if (parsed.getProname() == null || parsed.getProno() == null) {
            extractAllFieldsFallback(ewbCopyDiv.text(), parsed);
        }
    }

    /**
     * 提取所有章节（增强版）- 支持两种页面结构
     */
    private Map<String, String> extractSections(Element ewbCopyDiv) {
        Map<String, String> sections = new LinkedHashMap<>();

        // 策略1：查找包含strong标签的实际内容容器
        Element contentContainer = ewbCopyDiv;

        // 如果ewb-copy内部有div，并且这个div包含strong标签，则用这个div作为容器
        Element innerDiv = ewbCopyDiv.selectFirst("div");
        if (innerDiv != null && !innerDiv.select("strong").isEmpty()) {
            contentContainer = innerDiv;
            log.debug("使用内部div作为内容容器");
        }

        // 策略2：查找所有strong标签作为章节标题
        List<Element> strongElements = contentContainer.select("strong");
        if (strongElements.isEmpty()) {
            log.warn("未找到strong标签，无法提取章节");
            return sections;
        }

        log.debug("找到 {} 个strong标签作为章节标题", strongElements.size());

        // 遍历所有strong标签，提取每个章节内容
        for (int i = 0; i < strongElements.size(); i++) {
            Element strongEl = strongElements.get(i);
            String sectionTitle = strongEl.text().trim();

            // 跳过空标题
            if (sectionTitle.isEmpty()) continue;

            // 提取该章节内容（从当前strong标签到下一个strong标签之间的内容）
            StringBuilder contentBuilder = new StringBuilder();

            // 获取当前strong标签的父节点
            Element parent = strongEl.parent();
            Node currentNode = strongEl;

            // 如果是p标签内的strong，从p标签开始收集
            if ("p".equals(parent.tagName())) {
                currentNode = parent;
            }

            // 收集所有后续节点，直到遇到下一个strong标签或文档结束
            Node nextSibling = currentNode.nextSibling();
            while (nextSibling != null) {
                // 检查是否遇到下一个章节标题
                if (isNextSectionStart(nextSibling, strongElements, i)) {
                    break;
                }

                // 将节点内容添加到章节
                String text = getNodeText(nextSibling);
                if (!text.isEmpty()) {
                    contentBuilder.append(text);
                    if (!text.endsWith("\n") && !text.endsWith(" ")) {
                        contentBuilder.append(" ");
                    }
                }

                nextSibling = nextSibling.nextSibling();
            }

            String content = contentBuilder.toString().trim();

            // 清理内容：移除开头的特殊字符和空格
            content = content.replaceAll("^&nbsp;+", "")
                .replaceAll("^\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();

            if (!content.isEmpty()) {
                // 避免重复章节（如"一、"和"项目基本情况"可能是同一个章节）
                boolean isDuplicate = false;
                for (String existingTitle : sections.keySet()) {
                    if (existingTitle.contains(sectionTitle) || sectionTitle.contains(existingTitle)) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    sections.put(sectionTitle, content);
                    log.debug("提取章节: {} -> 长度: {}", sectionTitle, content.length());
                }
            }
        }

        // 策略3：如果没有提取到章节，尝试降级解析
        if (sections.isEmpty()) {
            log.warn("通过strong标签未提取到章节，尝试备选方法");
            return extractSectionsFallback(contentContainer);
        }

        return sections;
    }

    /**
     * 判断是否是下一个章节的开始
     */
    private boolean isNextSectionStart(Node node, List<Element> strongElements, int currentIndex) {
        if (node instanceof Element) {
            Element el = (Element) node;
            if ("strong".equals(el.tagName())) {
                return true;
            }

            // 检查是否是p标签包含strong标签
            if ("p".equals(el.tagName())) {
                Element strongInP = el.selectFirst("strong");
                if (strongInP != null) {
                    return true;
                }
            }
        }

        // 检查下一个strong标签是否在后续节点中
        if (currentIndex + 1 < strongElements.size()) {
            Element nextStrong = strongElements.get(currentIndex + 1);
            // 如果当前节点就是下一个strong标签，则停止
            if (node.equals(nextStrong)) {
                return true;
            }
            // 如果当前节点包含下一个strong标签，则停止
            if (node instanceof Element) {
                Element el = (Element) node;
                if (el.children().contains(nextStrong)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取节点的文本内容
     */
    private String getNodeText(Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).text();
        } else if (node instanceof Element) {
            Element el = (Element) node;
            // 跳过strong标签，因为那是章节标题
            if ("strong".equals(el.tagName())) {
                return "";
            }
            // 处理br标签，转换为换行
            if ("br".equals(el.tagName())) {
                return "\n";
            }
            // 处理p标签，递归获取内容并添加换行
            if ("p".equals(el.tagName())) {
                String text = el.text();
                return text.isEmpty() ? "" : text + "\n";
            }
            return el.text();
        }
        return "";
    }

    /**
     * 备选章节提取方法（用于处理特殊结构）
     */
    private Map<String, String> extractSectionsFallback(Element container) {
        Map<String, String> sections = new LinkedHashMap<>();

        // 使用正则匹配章节标题和内容
        String html = container.html();

        // 匹配模式：<p><strong>章节标题</strong></p>后面跟着的内容
        Pattern pattern = Pattern.compile(
            "<p>\\s*<strong>([^<]+)</strong>\\s*</p>\\s*(.*?)(?=<p>\\s*<strong>|$)",
            Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String sectionTitle = matcher.group(1).trim();
            String rawContent = matcher.group(2);

            // 清理HTML标签，只保留文本
            String content = Jsoup.parse(rawContent).text()
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();

            if (!content.isEmpty()) {
                sections.put(sectionTitle, content);
                log.debug("备选方法提取章节: {} -> 长度: {}", sectionTitle, content.length());
            }
        }

        return sections;
    }

    /**
     * 处理单个章节（增强版）
     */
    private void processSectionEnhanced(String sectionTitle, String content, TenderProjectDetailParsed parsed) {
        log.debug("处理章节: {}", sectionTitle);

        if (sectionTitle.contains("项目概况")) {
            parsed.setSectionProjectOverview(content);
        }
        else if (sectionTitle.contains("项目基本情况") || sectionTitle.contains("一、")) {
            parsed.setSectionBasicInfo(content);
            parseBasicInfoEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("申请人资格要求") || sectionTitle.contains("资格要求") || sectionTitle.contains("二、")) {
            parsed.setSectionQualification(content);
        }
        else if (sectionTitle.contains("获取招标文件") || sectionTitle.contains("三、")) {
            parsed.setSectionDocAcquisition(content);
            extractDocTimeRangeEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("提交投标文件截止时间、开标时间和地点") || sectionTitle.contains("四、")) {
            parsed.setSectionBiddingSchedule(content);
            extractBiddingInfoEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("公告期限") || sectionTitle.contains("五、")) {
            parsed.setSectionAnnouncementPeriod(content);
        }
        else if (sectionTitle.contains("其他补充事宜") || sectionTitle.contains("六、")) {
            parsed.setSectionOtherMatters(content);
        }
        else if (sectionTitle.contains("对本次招标提出询问") || sectionTitle.contains("联系方式") || sectionTitle.contains("七、")) {
            parsed.setSectionContact(content);
            extractContactInfoEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("采购需求")) {
            parsed.setSectionProjectNeed(content);
        }
    }

    /**
     * 解析基本情况章节（增强版）- 修复版
     */
    private void parseBasicInfoEnhanced(String content, TenderProjectDetailParsed parsed) {
        log.debug("解析基本情况章节: {}", content);

        // 方法1：先尝试按换行分割
        String[] lines = content.split("\n");

        // 如果没有换行，尝试按空格分割（但保留键值对的完整性）
        if (lines.length == 1 && content.contains("：")) {
            // 处理同一行中有多个键值对的情况
            lines = splitMultipleKeyValuePairs(content);
        }

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 尝试多种分隔符
            String[] separators = {"：", ":", "：", ":"};
            for (String separator : separators) {
                int separatorIndex = line.indexOf(separator);
                if (separatorIndex > 0) {
                    String label = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + separator.length()).trim();

                    // 如果value中包含下一个键的标识，需要截断
                    value = truncateValueBeforeNextKey(value, separators);

                    if (!label.isEmpty() && !value.isEmpty()) {
                        mapFieldEnhanced(label, value, parsed);
                        break; // 找到匹配的分隔符后跳出
                    }
                }
            }
        }

        // 执行正则备选解析，确保关键字段被正确提取
        parseBasicInfoRegexFallback(content, parsed);
    }

    /**
     * 处理同一行中有多个键值对的情况
     */
    private String[] splitMultipleKeyValuePairs(String content) {
        List<String> pairs = new ArrayList<>();

        // 常见的中文键名
        String[] keyPatterns = {
            "项目编号", "项目名称", "招标方式", "采购方式",
            "预算金额", "最高限价", "控制价", "最高投标限价",
            "采购需求", "招标范围", "合同履行期限",
            "是否接受联合体投标", "本项目是否接受联合体"
        };

        StringBuilder currentPair = new StringBuilder();
        int lastEndIndex = 0;

        // 查找所有键的开始位置
        List<Integer> keyPositions = new ArrayList<>();
        for (String key : keyPatterns) {
            int pos = content.indexOf(key);
            if (pos >= 0) {
                keyPositions.add(pos);
            }
        }
        Collections.sort(keyPositions);

        // 如果没有找到键，按原始内容返回
        if (keyPositions.isEmpty()) {
            return new String[]{content};
        }

        // 根据键位置分割
        for (int i = 0; i < keyPositions.size(); i++) {
            int startPos = keyPositions.get(i);
            int endPos = (i + 1 < keyPositions.size()) ? keyPositions.get(i + 1) : content.length();

            String pair = content.substring(startPos, endPos).trim();
            if (!pair.isEmpty()) {
                pairs.add(pair);
            }
        }

        return pairs.toArray(new String[0]);
    }

    /**
     * 截取值，避免包含下一个键的内容
     */
    private String truncateValueBeforeNextKey(String value, String[] separators) {
        // 常见键名列表
        String[] keyNames = {
            "项目编号", "项目名称", "招标方式", "采购方式",
            "预算金额", "最高限价", "控制价", "最高投标限价",
            "采购需求", "招标范围", "合同履行期限",
            "是否接受联合体投标", "本项目是否接受联合体",
            "申请人资格要求", "资格要求", "获取招标文件",
            "提交投标文件截止时间", "公告期限", "其他补充事宜",
            "对本次招标提出询问", "联系方式"
        };

        // 查找下一个键的起始位置
        int nextKeyIndex = -1;
        for (String key : keyNames) {
            int index = value.indexOf(key);
            if (index >= 0 && (nextKeyIndex == -1 || index < nextKeyIndex)) {
                nextKeyIndex = index;
            }
        }

        // 如果找到下一个键，截断到该位置之前
        if (nextKeyIndex > 0) {
            return value.substring(0, nextKeyIndex).trim();
        }

        return value.trim();
    }

    /**
     * 基本情况章节备选解析（正则提取）
     */
    private void parseBasicInfoRegexFallback(String content, TenderProjectDetailParsed parsed) {
        log.debug("执行基本情况正则备选解析");

        // 提取项目编号
        if (parsed.getProno() == null || parsed.getProno().isEmpty() || parsed.getProno().contains("项目名称")) {
            Pattern pattern = Pattern.compile("项目编号[：:]\\s*([A-Za-z0-9\\-]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String projectNo = matcher.group(1).trim();
                if (projectNo.length() > 5 && projectNo.length() < 50) {
                    parsed.setProno(projectNo);
                    log.debug("正则提取项目编号: {}", projectNo);
                }
            }
        }

        // 提取项目名称
        if (parsed.getProname() == null || parsed.getProname().isEmpty()) {
            Pattern pattern = Pattern.compile("项目名称[：:]\\s*([^\\n：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String projectName = matcher.group(1).trim();
                if (projectName.length() > 2 && projectName.length() < 100) {
                    parsed.setProname(projectName);
                    log.debug("正则提取项目名称: {}", projectName);
                }
            }
        }

        // 提取预算金额
        if (parsed.getBudgetAmount() == null) {
            Pattern pattern = Pattern.compile("预算金额[：:]\\s*([\\d,.]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String amountStr = matcher.group(1).replaceAll(",", "");
                parsed.setBudgetAmount(parseAmount(amountStr));
                log.debug("正则提取预算金额: {}", amountStr);
            }
        }

        // 提取采购方式
        if (parsed.getTenderMethod() == null || parsed.getTenderMethod().isEmpty()
            || parsed.getTenderMethod().contains("预算金额")) {
            Pattern pattern = Pattern.compile("(?:招标方式|采购方式)[：:]\\s*([^\\n：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String tenderMethod = matcher.group(1).trim();
                if (tenderMethod.length() > 1 && tenderMethod.length() < 50) {
                    parsed.setTenderMethod(tenderMethod);
                    log.debug("正则提取采购方式: {}", tenderMethod);
                }
            }
        }
    }

    /**
     * 字段映射（增强版）- 修复版
     */
    private void mapFieldEnhanced(String label, String value, TenderProjectDetailParsed parsed) {
        log.debug("映射字段: {} -> {}", label, value);

        // 清理标签，移除常见的后缀和空格
        String cleanedLabel = label.replaceAll("\\s+", "")
            .replaceAll("[:：]", "")
            .trim();

        // 根据清理后的标签映射字段
        if (cleanedLabel.contains("项目编号")) {
            if (parsed.getProno() == null || parsed.getProno().isEmpty()) {
                // 只取纯项目编号，排除其他内容
                String cleanValue = extractPureProjectNo(value);
                parsed.setProno(cleanValue);
            }
        }
        else if (cleanedLabel.contains("项目名称")) {
            if (parsed.getProname() == null || parsed.getProname().isEmpty()) {
                // 清理项目名称，只取项目名称本身
                String cleanValue = extractPureProjectName(value);
                parsed.setProname(cleanValue);
            }
        }
        else if (cleanedLabel.contains("预算金额")) {
            if (parsed.getBudgetAmount() == null) {
                parsed.setBudgetAmount(parseAmount(value));
            }
        }
        else if (cleanedLabel.contains("最高限价") || cleanedLabel.contains("控制价")
            || cleanedLabel.contains("最高投标限价")) {
            if (parsed.getBudgetAmount() == null) {
                parsed.setBudgetAmount(parseAmount(value));
            }
        }
        else if (cleanedLabel.contains("招标方式") || cleanedLabel.contains("采购方式")) {
            if (parsed.getTenderMethod() == null || parsed.getTenderMethod().isEmpty()) {
                // 只取采购方式本身，排除其他内容
                String cleanValue = extractPureTenderMethod(value);
                parsed.setTenderMethod(cleanValue);
            }
        }
        else if (cleanedLabel.contains("采购需求")) {
            if (parsed.getSectionProjectNeed() == null || parsed.getSectionProjectNeed().isEmpty()) {
                parsed.setSectionProjectNeed(value);
            }
        }
        else if (cleanedLabel.contains("合同履行期限")) {
            // 如果需要，可以存储到额外字段
            // parsed.setContractPeriod(value);
        }
    }

    /**
     * 提取纯项目编号（移除项目名称等其他内容）
     */
    private String extractPureProjectNo(String value) {
        // 如果包含其他键的标识，截断
        String[] stopPatterns = {"项目名称", "招标方式", "采购方式", "预算金额", "最高限价"};
        for (String pattern : stopPatterns) {
            int index = value.indexOf(pattern);
            if (index > 0) {
                return value.substring(0, index).trim();
            }
        }

        // 如果包含多个空格，可能只取第一部分
        String[] parts = value.split("\\s+");
        if (parts.length > 1) {
            // 检查第一部分是否像项目编号（包含字母数字和连字符）
            if (parts[0].matches(".*[A-Za-z0-9\\-]+.*")) {
                return parts[0];
            }
        }

        return value.trim();
    }

    /**
     * 提取纯项目名称
     */
    private String extractPureProjectName(String value) {
        // 如果包含其他键的标识，截断
        String[] stopPatterns = {"招标方式", "采购方式", "预算金额", "最高限价", "采购需求"};
        for (String pattern : stopPatterns) {
            int index = value.indexOf(pattern);
            if (index > 0) {
                return value.substring(0, index).trim();
            }
        }

        // 清理常见的后缀
        value = value.replaceAll("招标$", "")
            .replaceAll("采购$", "")
            .replaceAll("项目$", "")
            .replaceAll("公告$", "")
            .trim();

        return value.trim();
    }

    /**
     * 提取纯采购方式
     */
    private String extractPureTenderMethod(String value) {
        // 如果包含其他键的标识，截断
        String[] stopPatterns = {"预算金额", "最高限价", "采购需求", "合同履行期限"};
        for (String pattern : stopPatterns) {
            int index = value.indexOf(pattern);
            if (index > 0) {
                return value.substring(0, index).trim();
            }
        }

        return value.trim();
    }

    /**
     * 提取文件发售时间（增强版）
     */
    private void extractDocTimeRangeEnhanced(String content, TenderProjectDetailParsed parsed) {
        Pattern pattern = Pattern.compile("时间[：:]\\s*(\\d{4}-\\d{2}-\\d{2})\\s*至\\s*(\\d{4}-\\d{2}-\\d{2})");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            parsed.setDocStartTime(parseDateTime(matcher.group(1) + " 00:00:00"));
            parsed.setDocEndTime(parseDateTime(matcher.group(2) + " 23:59:59"));
        }
    }

    /**
     * 提取投标信息（增强版）
     */
    private void extractBiddingInfoEnhanced(String content, TenderProjectDetailParsed parsed) {
        log.debug("提取投标信息: {}", content);

        Pattern timePattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})");
        Matcher m = timePattern.matcher(content);
        if (m.find()) {
            String timeStr = m.group(1) + ":00";
            parsed.setBiddingDeadline(parseDateTime(timeStr));
            parsed.setKaibiaodate(parseDateTime(timeStr));
        }

        if (content.contains("地点：")) {
            int startIdx = content.indexOf("地点：");
            String afterLocation = content.substring(startIdx + 3);
            // 取地点后的第一行
            String[] lines = afterLocation.split("\n");
            if (lines.length > 0) {
                String location = lines[0].trim();
                // 清理可能的多余内容
                location = location.split("\\s+")[0];
                parsed.setChangdi(location);
            }
        }
    }

    /**
     * 提取联系信息（增强版）
     */
    /**
     * 提取联系信息（增强版）- 修复连续文本问题
     */
    private void extractContactInfoEnhanced(String content, TenderProjectDetailParsed parsed) {
        log.debug("提取联系信息: {}", content);

        // 如果内容中没有换行符，但包含数字编号（如1. 2. 3.），则按数字编号分割
        if (!content.contains("\n") && content.matches(".*\\d+\\..*")) {
            // 按数字编号分割（如1. 2. 3.）
            String[] sections = content.split("(?=\\d+\\.)");
            for (String section : sections) {
                processContactSection(section.trim(), parsed);
            }
        } else {
            // 使用原有逻辑，按换行分割
            String[] lines = content.split("\n");
            String currentSection = null;
            StringBuilder currentContent = new StringBuilder();

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 识别当前部分
                if (line.contains("采购人信息")) {
                    currentSection = "purchaser";
                    currentContent = new StringBuilder();
                } else if (line.contains("采购代理机构信息")) {
                    currentSection = "agent";
                    currentContent = new StringBuilder();
                } else if (line.contains("项目联系方式")) {
                    currentSection = "project";
                    currentContent = new StringBuilder();
                }

                if (currentSection != null) {
                    currentContent.append(line).append(" ");
                }
            }

            // 处理收集到的内容
            if (currentContent.length() > 0) {
                processContactSection(currentContent.toString(), parsed);
            }
        }

        // 如果还是没有提取到，尝试更通用的方法
        extractContactInfoFallback(content, parsed);
    }

    /**
     * 处理联系信息部分
     */
    private void processContactSection(String section, TenderProjectDetailParsed parsed) {
        log.debug("处理联系信息部分: {}", section);

        // 判断是哪一部分
        boolean isPurchaser = section.contains("采购人信息");
        boolean isAgent = section.contains("采购代理机构信息");
        boolean isProject = section.contains("项目联系方式");

        // 提取名称
        if ((isPurchaser || isAgent || isProject) && section.contains("名称：")) {
            String name = extractContactField(section, "名称：");
            if (isPurchaser && (parsed.getPurchaser() == null || parsed.getPurchaser().isEmpty())) {
                parsed.setPurchaser(name);
                log.debug("提取采购人名称: {}", name);
            } else if (isAgent && (parsed.getAgentCompany() == null || parsed.getAgentCompany().isEmpty())) {
                parsed.setAgentCompany(name);
                log.debug("提取代理机构名称: {}", name);
            }
        }

        // 提取地址
        if (section.contains("地址：")) {
            String address = extractContactField(section, "地址：");
            if (isPurchaser && (parsed.getPurchaserAddress() == null || parsed.getPurchaserAddress().isEmpty())) {
                parsed.setPurchaserAddress(address);
                log.debug("提取采购人地址: {}", address);
            } else if (isAgent && (parsed.getAgentAddress() == null || parsed.getAgentAddress().isEmpty())) {
                parsed.setAgentAddress(address);
                log.debug("提取代理机构地址: {}", address);
            }
        }

        // 提取联系方式/电话
        if (section.contains("联系方式：")) {
            String phone = extractContactField(section, "联系方式：");
            if (isPurchaser && (parsed.getPurchaserPhone() == null || parsed.getPurchaserPhone().isEmpty())) {
                parsed.setPurchaserPhone(phone);
                log.debug("提取采购人电话: {}", phone);
            } else if (isAgent && (parsed.getAgentPhone() == null || parsed.getAgentPhone().isEmpty())) {
                parsed.setAgentPhone(phone);
                log.debug("提取代理机构电话: {}", phone);
            }
        }

        // 提取项目联系人
        if (isProject && section.contains("项目联系人：")) {
            String contact = extractContactField(section, "项目联系人：");
            if (parsed.getProjectContact() == null || parsed.getProjectContact().isEmpty()) {
                parsed.setProjectContact(contact);
                log.debug("提取项目联系人: {}", contact);
            }
        }

        // 提取项目电话
        if (isProject && section.contains("电话：")) {
            String phone = extractContactField(section, "电话：");
            if (parsed.getProjectPhone() == null || parsed.getProjectPhone().isEmpty() ||
                parsed.getProjectPhone().equals("0512-58188537")) {
                parsed.setProjectPhone(phone);
                log.debug("提取项目电话: {}", phone);
            }
        }
    }

    /**
     * 提取联系信息字段
     */
    private String extractContactField(String text, String prefix) {
        int idx = text.indexOf(prefix);
        if (idx >= 0) {
            // 从前缀后面开始
            String remaining = text.substring(idx + prefix.length()).trim();

            // 查找下一个字段的开始（常见的联系信息字段）
            String[] nextFields = {"名称：", "地址：", "联系方式：", "项目联系人：", "电话：", "1.", "2.", "3."};
            int minNextIdx = Integer.MAX_VALUE;

            for (String nextField : nextFields) {
                int nextIdx = remaining.indexOf(nextField);
                if (nextIdx >= 0 && nextIdx < minNextIdx) {
                    minNextIdx = nextIdx;
                }
            }

            // 截取字段值
            String value;
            if (minNextIdx < Integer.MAX_VALUE) {
                value = remaining.substring(0, minNextIdx).trim();
            } else {
                value = remaining.trim();
            }

            // 清理值：移除可能的多余内容
            value = value.replaceAll("\\s+\\d+\\.", "").trim();
            return value;
        }
        return "";
    }

    /**
     * 联系信息备选提取方法（正则提取）
     */
    private void extractContactInfoFallback(String content, TenderProjectDetailParsed parsed) {
        log.debug("执行联系信息备选提取");

        // 提取采购人信息
        if (parsed.getPurchaser() == null || parsed.getPurchaser().isEmpty()) {
            Pattern pattern = Pattern.compile("采购人信息[\\s\\S]*?名称[：:]\\s*([^\\s：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String purchaser = matcher.group(1).trim();
                if (purchaser.length() > 1 && purchaser.length() < 100) {
                    parsed.setPurchaser(purchaser);
                    log.debug("备选提取采购人: {}", purchaser);
                }
            }
        }

        if (parsed.getPurchaserAddress() == null || parsed.getPurchaserAddress().isEmpty()) {
            Pattern pattern = Pattern.compile("采购人信息[\\s\\S]*?地址[：:]\\s*([^\\s：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                parsed.setPurchaserAddress(matcher.group(1).trim());
            }
        }

        if (parsed.getPurchaserPhone() == null || parsed.getPurchaserPhone().isEmpty()) {
            Pattern pattern = Pattern.compile("采购人信息[\\s\\S]*?联系方式[：:]\\s*([\\d-]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                parsed.setPurchaserPhone(matcher.group(1).trim());
            }
        }

        // 提取代理机构信息
        if (parsed.getAgentCompany() == null || parsed.getAgentCompany().isEmpty()) {
            Pattern pattern = Pattern.compile("采购代理机构信息[\\s\\S]*?名称[：:]\\s*([^\\s：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String agent = matcher.group(1).trim();
                if (agent.length() > 1 && agent.length() < 100) {
                    parsed.setAgentCompany(agent);
                    log.debug("备选提取代理机构: {}", agent);
                }
            }
        }

        if (parsed.getAgentAddress() == null || parsed.getAgentAddress().isEmpty()) {
            Pattern pattern = Pattern.compile("采购代理机构信息[\\s\\S]*?地址[：:]\\s*([^\\s：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                parsed.setAgentAddress(matcher.group(1).trim());
            }
        }

        if (parsed.getAgentPhone() == null || parsed.getAgentPhone().isEmpty()) {
            Pattern pattern = Pattern.compile("采购代理机构信息[\\s\\S]*?联系方式[：:]\\s*([\\d-]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                parsed.setAgentPhone(matcher.group(1).trim());
            }
        }

        // 提取项目联系人
        if (parsed.getProjectContact() == null || parsed.getProjectContact().isEmpty()) {
            Pattern pattern = Pattern.compile("项目联系人[：:]\\s*([^\\s：:]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                parsed.setProjectContact(matcher.group(1).trim());
            }
        }

        // 提取项目电话
        if (parsed.getProjectPhone() == null || parsed.getProjectPhone().isEmpty() ||
            parsed.getProjectPhone().equals("0512-58188537")) {
            Pattern pattern = Pattern.compile("项目联系方式[\\s\\S]*?电话[：:]\\s*([\\d-]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                parsed.setProjectPhone(matcher.group(1).trim());
            }
        }
    }

    /**
     * 从字符串中提取冒号后的值
     */
    private String extractValueAfterColon(String line, String prefix) {
        int idx = line.indexOf(prefix);
        if (idx >= 0) {
            String value = line.substring(idx + prefix.length()).trim();
            // 清理可能的多余内容（只取第一行）
            return value.split("\\s+")[0];
        }
        return "";
    }

    /**
     * 从项目概况中提取投标截止时间
     */
    private void extractDeadlineFromOverview(String content, TenderProjectDetailParsed parsed) {
        Pattern pattern = Pattern.compile("于\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})\\(北京时间\\)前递交");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            parsed.setBiddingDeadline(parseDateTime(matcher.group(1) + ":00"));
        }
    }

    /**
     * 全局兜底提取 - 增强版
     */
    private void extractAllFieldsFallback(String fullText, TenderProjectDetailParsed parsed) {
        log.debug("执行全局兜底提取");

        // 提取项目编号（如果之前提取的有问题）
        if (parsed.getProno() == null || parsed.getProno().isEmpty()
            || parsed.getProno().contains("项目名称") || parsed.getProno().contains("招标方式")) {
            Pattern pattern = Pattern.compile("项目编号[：:]\\s*([A-Za-z0-9\\-]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String projectNo = matcher.group(1).trim();
                if (projectNo.length() > 5 && projectNo.length() < 50) {
                    parsed.setProno(projectNo);
                    log.debug("全局兜底提取项目编号: {}", projectNo);
                }
            }
        }

        // 提取项目名称
        if (parsed.getProname() == null || parsed.getProname().isEmpty()) {
            Pattern pattern = Pattern.compile("项目名称[：:]\\s*([^\\n：:]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String projectName = matcher.group(1).trim();
                if (projectName.length() > 2 && projectName.length() < 100) {
                    parsed.setProname(projectName);
                    log.debug("全局兜底提取项目名称: {}", projectName);
                }
            }
        }

        // 提取采购方式（如果之前提取的有问题）
        if (parsed.getTenderMethod() == null || parsed.getTenderMethod().isEmpty()
            || parsed.getTenderMethod().contains("预算金额") || parsed.getTenderMethod().contains("采购需求")) {
            Pattern pattern = Pattern.compile("(?:招标方式|采购方式)[：:]\\s*([^\\n：:]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String tenderMethod = matcher.group(1).trim();
                if (tenderMethod.length() > 1 && tenderMethod.length() < 50) {
                    parsed.setTenderMethod(tenderMethod);
                    log.debug("全局兜底提取采购方式: {}", tenderMethod);
                }
            }
        }

        if (parsed.getPurchaser() == null || parsed.getPurchaser().isEmpty()) {
            Pattern pattern = Pattern.compile("采购人信息[\\s\\S]*?名称：\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                parsed.setPurchaser(matcher.group(1).trim());
            }
        }

        if (parsed.getAgentCompany() == null || parsed.getAgentCompany().isEmpty()) {
            Pattern pattern = Pattern.compile("采购代理机构信息[\\s\\S]*?名称：\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                parsed.setAgentCompany(matcher.group(1).trim());
            }
        }

        if (parsed.getProjectPhone() == null || parsed.getProjectPhone().isEmpty() ||
            parsed.getProjectPhone().equals("0512-58188537")) {
            Pattern pattern = Pattern.compile("项目联系方式[\\s\\S]*?电话：\\s*([\\d-]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                parsed.setProjectPhone(matcher.group(1).trim());
            }
        }

        if (parsed.getKaibiaodate() == null) {
            Pattern pattern = Pattern.compile("四、[^\\n]*开标时间[：:]\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                parsed.setKaibiaodate(parseDateTime(matcher.group(1) + ":00"));
            }
        }

        if (parsed.getChangdi() == null || parsed.getChangdi().isEmpty()) {
            Pattern pattern = Pattern.compile("四、[^\\n]*地点[：:]\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                parsed.setChangdi(matcher.group(1).trim());
            }
        }

        if (parsed.getBudgetAmount() == null) {
            Pattern pattern = Pattern.compile("预算金额[：:]\\s*([\\d,.]+)");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String amountStr = matcher.group(1).replaceAll(",", "");
                parsed.setBudgetAmount(parseAmount(amountStr));
            }
        }



            // 增强的联系方式提取（如果之前没提取到）
            if (parsed.getPurchaser() == null || parsed.getPurchaser().isEmpty()) {
                // 更精确的采购人提取
                Pattern pattern = Pattern.compile("1\\.采购人信息[\\s\\S]*?名称[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    String purchaser = matcher.group(1).trim();
                    if (purchaser.length() > 1 && purchaser.length() < 100) {
                        parsed.setPurchaser(purchaser);
                    }
                }
            }

            if (parsed.getPurchaserAddress() == null || parsed.getPurchaserAddress().isEmpty()) {
                Pattern pattern = Pattern.compile("1\\.采购人信息[\\s\\S]*?地址[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    parsed.setPurchaserAddress(matcher.group(1).trim());
                }
            }

            if (parsed.getPurchaserPhone() == null || parsed.getPurchaserPhone().isEmpty()) {
                Pattern pattern = Pattern.compile("1\\.采购人信息[\\s\\S]*?联系方式[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    parsed.setPurchaserPhone(matcher.group(1).trim());
                }
            }

            if (parsed.getAgentCompany() == null || parsed.getAgentCompany().isEmpty()) {
                Pattern pattern = Pattern.compile("2\\.采购代理机构信息[\\s\\S]*?名称[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    String agent = matcher.group(1).trim();
                    if (agent.length() > 1 && agent.length() < 100) {
                        parsed.setAgentCompany(agent);
                    }
                }
            }

            if (parsed.getAgentAddress() == null || parsed.getAgentAddress().isEmpty()) {
                Pattern pattern = Pattern.compile("2\\.采购代理机构信息[\\s\\S]*?地址[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    parsed.setAgentAddress(matcher.group(1).trim());
                }
            }

            if (parsed.getAgentPhone() == null || parsed.getAgentPhone().isEmpty()) {
                Pattern pattern = Pattern.compile("2\\.采购代理机构信息[\\s\\S]*?联系方式[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    parsed.setAgentPhone(matcher.group(1).trim());
                }
            }

            if (parsed.getProjectContact() == null || parsed.getProjectContact().isEmpty()) {
                Pattern pattern = Pattern.compile("3\\.项目联系方式[\\s\\S]*?项目联系人[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    parsed.setProjectContact(matcher.group(1).trim());
                }
            }

            if (parsed.getProjectPhone() == null || parsed.getProjectPhone().isEmpty() ||
                parsed.getProjectPhone().equals("0512-58188537")) {
                Pattern pattern = Pattern.compile("3\\.项目联系方式[\\s\\S]*?电话[：:]\\s*([^\\n：:]+)");
                Matcher matcher = pattern.matcher(fullText);
                if (matcher.find()) {
                    parsed.setProjectPhone(matcher.group(1).trim());
                }
            }


    }

    private String fetchHtml(String url) throws Exception {
        CloseableHttpClient client = HebeiHttpClientCommonFactory.getClient();
        HttpGet request = new HttpGet(url);
        String token = HebeiTokenCommonManager.getToken();
        if (token != null && !token.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + token);
        }
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        request.setHeader("Referer", "http://ssl.hebpr.cn/hbggfwpt/jydt/salesPlat.html");
        try (CloseableHttpResponse response = client.execute(request)) {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            text = text.replaceAll("[^0-9.]", "");
            if (!text.isEmpty()) {
                // 处理可能的万元单位
                if (text.matches(".*[万wW].*")) {
                    text = text.replaceAll("[万wW]", "");
                    return new BigDecimal(text).multiply(new BigDecimal("10000"));
                }
                return new BigDecimal(text);
            }
        } catch (Exception e) {
            log.warn("解析金额失败：{}", text);
        }
        return null;
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isEmpty()) return null;

        if (text.contains("阅读次数") || text.contains("信息来源")) {
            return null;
        }

        try {
            String normalized = text.trim();
            normalized = normalized.replaceAll("[年月]", "-").replaceAll("[日号]", " ").replaceAll("[时分秒]", ":").replaceAll("[:\\s]+$", "");

            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    if (normalized.length() == 10) {
                        return LocalDateTime.of(LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE), LocalTime.MIN);
                    }
                    return LocalDateTime.parse(normalized, formatter);
                } catch (DateTimeParseException ignored) { }
            }
        } catch (Exception e) {
            log.warn("解析日期时间失败：{}", text);
        }
        return null;
    }
}
