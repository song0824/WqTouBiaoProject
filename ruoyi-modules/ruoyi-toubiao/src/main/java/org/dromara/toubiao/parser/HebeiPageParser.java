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
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
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

    // 新增：Word风格HTML检测
    private static final Pattern PATTERN_WORD_STYLE = Pattern.compile("class=\"MsoNormal\"", Pattern.CASE_INSENSITIVE);

    /**
     * 解析页面（带原始项目名称）
     * @param infoid 信息ID
     * @param infoUrl 页面URL
     * @param originalProname 原始项目名称（从数据库查询得到）
     * @return 解析结果
     */
    public TenderProjectDetailParsed parse(String infoid, String infoUrl, String originalProname) {
        TenderProjectDetailParsed parsed = new TenderProjectDetailParsed();
        parsed.setInfoid(infoid);
        parsed.setInfoUrl(infoUrl);
        parsed.setParseTime(LocalDateTime.now());

        try {
            log.info("开始解析河北页面：{}", infoUrl);
            String html = fetchHtml(infoUrl);
            if (html == null || html.isEmpty()) {
                parsed.setParseStatus(3); // 解析失败
                parsed.setParseErrorMsg("获取HTML内容失败或为空");
                return parsed;
            }

            Document doc = Jsoup.parse(html);

            // 1. 总是先提取顶部信息（发布时间和地区）
            extractInfoFromIntro(doc, parsed);

            Element ewbCopyDiv = doc.selectFirst("div.ewb-copy");
            if (ewbCopyDiv == null) {
                log.warn("页面结构异常：未找到核心内容区域 div.ewb-copy, url: {}", infoUrl);
                parsed.setParseStatus(3);
                parsed.setParseErrorMsg("页面结构不符合预期，缺少 div.ewb-copy");

                // 即使失败也尝试提取一些基本信息
                extractBasicInfoFromNonStandard(doc.body(), parsed, originalProname);
                return parsed;
            }

            // 2. 【重要】检查是否为标准格式招标文件，不是则跳过
            if (!isStandardTenderPage(ewbCopyDiv)) {
                log.info("检测到非标准格式页面，标记为跳过，url: {}", infoUrl);
                parsed.setParseStatus(4); // 4表示跳过解析（非标准格式）
                parsed.setParseErrorMsg("非标准格式招标文件，跳过解析");

                // 即使跳过，也尝试提取一些基本信息
                extractBasicInfoFromNonStandard(ewbCopyDiv, parsed, originalProname);
                return parsed;
            }

            // 3. 执行解析
            parseEnhanced(ewbCopyDiv, parsed);

            // 4. 验证和修复解析结果
            validateAndFixParsedData(parsed, originalProname);

            parsed.setParseStatus(2); // 解析成功

            log.info("页面解析成功：{}", infoid);
        } catch (Exception e) {
            log.error("页面解析失败：{}", infoUrl, e);
            parsed.setParseStatus(3); // 解析失败
            parsed.setParseErrorMsg("解析异常: " + e.getMessage());

            // 即使异常，也设置一些基本字段
            if ((parsed.getProname() == null || parsed.getProname().isEmpty())
                && originalProname != null && !originalProname.isEmpty()) {
                parsed.setProname(originalProname);
            } else if (parsed.getProname() == null) {
                // 从URL或其他地方提取项目名称
                parsed.setProname(extractProjectNameFromUrl(infoUrl));
            }
        }
        return parsed;
    }

    /**
     * 兼容方法：解析页面（不带原始项目名称）
     * @param infoid 信息ID
     * @param infoUrl 页面URL
     * @return 解析结果
     */
    public TenderProjectDetailParsed parse(String infoid, String infoUrl) {
        return parse(infoid, infoUrl, null);
    }

    /**
     * 增强版解析方法
     */
    private void parseEnhanced(Element ewbCopyDiv, TenderProjectDetailParsed parsed) {
        // 先检查是否是Word风格HTML
        boolean isWordStyle = isWordStyleDocument(ewbCopyDiv);

        if (isWordStyle) {
            log.info("检测到Word风格HTML，使用专门解析方法");
            parseWordStyleDocument(ewbCopyDiv, parsed);
        } else {
            // 原有的解析逻辑
            Map<String, String> sections = extractSections(ewbCopyDiv);

            log.info("提取到 {} 个章节: {}", sections.size(), sections.keySet());

            // 如果没有提取到章节，尝试使用备用方法
            if (sections.isEmpty()) {
                log.info("使用备用方法提取章节");
                sections = extractSectionsByBr(ewbCopyDiv);
                log.info("备用方法提取到 {} 个章节: {}", sections.size(), sections.keySet());
            }

            for (Map.Entry<String, String> entry : sections.entrySet()) {
                log.debug("处理章节: {} -> 长度: {}", entry.getKey(), entry.getValue().length());
                processSectionEnhanced(entry.getKey(), entry.getValue(), parsed);
            }

            if (parsed.getBiddingDeadline() == null && sections.containsKey("项目概况")) {
                extractDeadlineFromOverview(sections.get("项目概况"), parsed);
            }
        }

        // 全局兜底提取
        if (parsed.getProname() == null || parsed.getProno() == null) {
            extractAllFieldsFallback(ewbCopyDiv.text(), parsed);
        }
    }

    /**
     * 通过br标签分割的方式提取章节（处理没有strong标签的情况）
     */
    private Map<String, String> extractSectionsByBr(Element ewbCopyDiv) {
        Map<String, String> sections = new LinkedHashMap<>();

        // 检查是否是表格结构
        if (ewbCopyDiv.select("table").size() > 0) {
            log.info("检测到表格结构，使用表格解析方法");
            return extractSectionsFromTable(ewbCopyDiv);
        }

        // 获取所有文本节点，按br分割
        String html = ewbCopyDiv.html();

        // 清理HTML，将多个连续的br合并为一个换行符
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        String text = Jsoup.parse(html).text();

        // 按换行分割
        String[] lines = text.split("\n");

        StringBuilder currentSectionContent = new StringBuilder();
        String currentSectionTitle = null;

        // 定义章节标题模式
        Pattern chapterPattern = Pattern.compile(
            "^\\s*[一二三四五六七八九十]、\\s*|" +
                "^\\s*(项目基本情况|申请人资格要求|获取招标文件|提交投标文件截止时间、开标时间和地点|公告期限|其他补充事宜|对本次招标提出询问|联系方式|项目概况)"
        );

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 检查是否是章节标题
            Matcher matcher = chapterPattern.matcher(line);
            if (matcher.find()) {
                // 保存前一章节
                if (currentSectionTitle != null) {
                    sections.put(currentSectionTitle, currentSectionContent.toString().trim());
                }

                // 开始新章节
                currentSectionTitle = line;
                currentSectionContent = new StringBuilder();
            } else if (currentSectionTitle != null) {
                // 添加到当前章节内容
                currentSectionContent.append(line).append("\n");
            } else {
                // 在第一个章节标题之前的内容，作为项目概况
                if (sections.containsKey("项目概况")) {
                    String existing = sections.get("项目概况");
                    sections.put("项目概况", existing + "\n" + line);
                } else {
                    sections.put("项目概况", line);
                }
            }
        }

        // 保存最后一个章节
        if (currentSectionTitle != null && currentSectionContent.length() > 0) {
            sections.put(currentSectionTitle, currentSectionContent.toString().trim());
        }

        return sections;
    }

    /**
     * 从表格结构中提取章节
     */
    private Map<String, String> extractSectionsFromTable(Element container) {
        Map<String, String> sections = new LinkedHashMap<>();

        Element table = container.selectFirst("table");
        if (table == null) {
            return sections;
        }

        StringBuilder allContent = new StringBuilder();

        // 提取表格中所有文本
        for (Element tr : table.select("tr")) {
            String rowText = tr.text().trim();
            if (!rowText.isEmpty()) {
                allContent.append(rowText).append("\n");

                // 尝试识别关键字段
                if (rowText.contains("项目编号：")) {
                    sections.put("项目编号", rowText);
                } else if (rowText.contains("项目名称：")) {
                    sections.put("项目名称", rowText);
                } else if (rowText.contains("预算金额：")) {
                    sections.put("预算金额", rowText);
                } else if (rowText.contains("采购人名称：")) {
                    sections.put("采购人信息", rowText);
                } else if (rowText.contains("采购代理机构全称：")) {
                    sections.put("代理机构信息", rowText);
                } else if (rowText.contains("获取文件时间：")) {
                    sections.put("获取招标文件", rowText);
                } else if (rowText.contains("投标截止时间：")) {
                    sections.put("投标截止时间", rowText);
                } else if (rowText.contains("开标时间：")) {
                    sections.put("开标时间", rowText);
                } else if (rowText.contains("开标地点：")) {
                    sections.put("开标地点", rowText);
                }
            }
        }

        // 如果没有识别到特定章节，将所有内容放入"项目概况"
        if (sections.isEmpty() && allContent.length() > 0) {
            sections.put("项目概况", allContent.toString().trim());
        }

        log.debug("表格解析提取到 {} 个章节", sections.size());
        return sections;
    }

    /**
     * 判断是否为Word风格文档
     */
    private boolean isWordStyleDocument(Element container) {
        // 检查是否有Word特有的class
        boolean hasMsoClass = container.select(".MsoNormal").size() > 0;
        boolean hasTabStops = container.html().contains("tab-stops");
        boolean hasMsoList = container.html().contains("mso-list");

        log.debug("Word风格检测: MsoClass={}, TabStops={}, MsoList={}", hasMsoClass, hasTabStops, hasMsoList);

        return hasMsoClass || hasTabStops || hasMsoList;
    }

    /**
     * 解析Word风格文档
     */
    private void parseWordStyleDocument(Element container, TenderProjectDetailParsed parsed) {
        log.info("开始解析Word风格文档");

        // 1. 解析基本情况章节
        parseWordStyleBasicInfo(container, parsed);

        // 2. 解析其他章节（资格要求、获取招标文件等）
        parseOtherWordSections(container, parsed);

        // 3. 解析联系信息
        parseContactInfoFromWord(container, parsed);

        // 4. 验证和修复关键字段
        validateWordStyleFields(parsed);
    }

    /**
     * 解析Word风格的基本情况章节
     */
    private void parseWordStyleBasicInfo(Element container, TenderProjectDetailParsed parsed) {
        log.debug("开始解析Word风格的基本情况章节");

        // 查找包含"项目基本情况"的h2标签
        Element basicInfoSection = null;

        for (Element h2 : container.select("h2")) {
            String h2Text = h2.text().trim();
            if (h2Text.contains("项目基本情况") || h2Text.contains("一、")) {
                basicInfoSection = h2;
                log.debug("找到基本情况章节标题: {}", h2Text);
                break;
            }
        }

        if (basicInfoSection == null) {
            log.warn("未找到基本情况章节标题");
            return;
        }

        // 从h2开始，获取其后的所有兄弟节点，直到下一个h2
        List<Element> basicInfoElements = new ArrayList<>();
        Node currentNode = basicInfoSection.nextSibling();

        while (currentNode != null) {
            if (currentNode instanceof Element) {
                Element element = (Element) currentNode;

                // 如果遇到下一个章节标题，停止
                if ("h2".equals(element.tagName())) {
                    break;
                }

                // 只收集p标签（包含字段信息）
                if ("p".equals(element.tagName())) {
                    String text = element.text().trim();
                    if (!text.isEmpty() && (text.contains("：") || text.contains(":"))) {
                        basicInfoElements.add(element);
                    }
                }
            }
            currentNode = currentNode.nextSibling();
        }

        log.info("找到基本情况段落数: {}", basicInfoElements.size());

        // 解析每个段落
        for (int i = 0; i < basicInfoElements.size(); i++) {
            Element pElement = basicInfoElements.get(i);
            String paragraphText = pElement.text().trim();
            log.debug("解析段落 {}: {}", i + 1, paragraphText);

            // 检查是否包含下划线内容（Word中通常用<u>表示需要填写的内容）
            List<Element> underlineElements = pElement.select("u");

            if (!underlineElements.isEmpty()) {
                // 使用下划线解析方法
                parseUnderlinedParagraph(pElement, parsed);
            } else {
                // 使用纯文本解析方法
                parsePlainTextParagraph(paragraphText, parsed);
            }
        }

        // 特别处理项目预算金额行（可能在同一行中有多个字段）
        for (Element pElement : basicInfoElements) {
            String text = pElement.text().trim();
            if (text.contains("项目预算金额") && text.contains("最高限价")) {
                parseBudgetAndLimitPriceLine(text, parsed);
            }
        }
    }

    /**
     * 解析带下划线的段落
     */
    private void parseUnderlinedParagraph(Element pElement, TenderProjectDetailParsed parsed) {
        String html = pElement.html();
        String text = pElement.text().trim();

        // 获取所有下划线元素
        List<Element> underlineElements = pElement.select("u");

        for (Element uElement : underlineElements) {
            // 获取字段名（u标签之前的内容）
            String fieldName = extractFieldNameBeforeElement(uElement);

            // 获取字段值（u标签内的内容）
            String fieldValue = uElement.text().trim();

            if (!fieldName.isEmpty() && !fieldValue.isEmpty()) {
                log.debug("下划线字段解析: {} -> {}", fieldName, fieldValue);
                mapFieldByChineseName(fieldName, fieldValue, parsed);
            }
        }
    }

    /**
     * 提取元素之前的字段名
     */
    private String extractFieldNameBeforeElement(Element element) {
        StringBuilder fieldName = new StringBuilder();
        Node prevNode = element.previousSibling();

        while (prevNode != null) {
            if (prevNode instanceof TextNode) {
                String text = ((TextNode) prevNode).text();
                fieldName.insert(0, text);
            }
            prevNode = prevNode.previousSibling();
        }

        // 清理字段名
        String name = fieldName.toString().trim();
        name = name.replaceAll("\\d+\\.", "")  // 移除序号
            .replaceAll("[：:]$", "")     // 移除末尾的冒号
            .replaceAll("^[、.]", "")    // 移除开头的标点
            .trim();

        return name;
    }

    /**
     * 解析纯文本段落
     */
    private void parsePlainTextParagraph(String text, TenderProjectDetailParsed parsed) {
        // 尝试按冒号分割
        String[] parts = text.split("[：:]", 2);
        if (parts.length == 2) {
            String fieldName = parts[0].trim();
            String fieldValue = parts[1].trim();

            // 清理字段名（移除序号）
            fieldName = fieldName.replaceAll("^\\d+\\.", "");

            if (!fieldName.isEmpty() && !fieldValue.isEmpty()) {
                log.debug("纯文本字段解析: {} -> {}", fieldName, fieldValue);
                mapFieldByChineseName(fieldName, fieldValue, parsed);
            }
        }
    }

    /**
     * 解析预算金额和最高限价在同一行的情况
     */
    private void parseBudgetAndLimitPriceLine(String text, TenderProjectDetailParsed parsed) {
        log.debug("处理预算金额和最高限价行: {}", text);

        // 提取预算金额
        Pattern budgetPattern = Pattern.compile("项目预算金额[：:]\\s*([^，,]+)");
        Matcher budgetMatcher = budgetPattern.matcher(text);
        if (budgetMatcher.find()) {
            String budgetStr = budgetMatcher.group(1).trim();
            BigDecimal budgetAmount = parseAmount(budgetStr);
            if (budgetAmount != null && parsed.getBudgetAmount() == null) {
                parsed.setBudgetAmount(budgetAmount);
                log.info("解析预算金额: {} -> {}", budgetStr, budgetAmount);
            }
        }

        // 提取最高限价（可选）
        Pattern limitPattern = Pattern.compile("最高限价[：:]\\s*([^，,]+)");
        Matcher limitMatcher = limitPattern.matcher(text);
        if (limitMatcher.find()) {
            String limitStr = limitMatcher.group(1).trim();
            // 可以存储到额外字段，这里只记录日志
            log.debug("解析最高限价: {}", limitStr);
        }
    }

    /**
     * 根据中文字段名映射到实体字段
     */
    private void mapFieldByChineseName(String fieldName, String fieldValue, TenderProjectDetailParsed parsed) {
        log.debug("映射中文字段: {} -> {}", fieldName, fieldValue);

        fieldName = fieldName.trim();
        fieldValue = fieldValue.trim();

        if (fieldName.contains("项目编号")) {
            if (parsed.getProno() == null || parsed.getProno().isEmpty()) {
                // 清理项目编号
                String cleanValue = cleanFieldValue(fieldValue);
                parsed.setProno(cleanValue);
            }
        } else if (fieldName.contains("项目名称")) {
            if (parsed.getProname() == null || parsed.getProname().isEmpty()) {
                // 清理项目名称，确保不包含其他字段
                String cleanValue = cleanWordStyleProjectName(fieldValue);
                parsed.setProname(cleanValue);
            }
        } else if (fieldName.contains("预算金额")) {
            if (parsed.getBudgetAmount() == null) {
                BigDecimal amount = parseAmount(fieldValue);
                if (amount != null) {
                    parsed.setBudgetAmount(amount);
                }
            }
        } else if (fieldName.contains("最高限价") || fieldName.contains("最高投标限价") || fieldName.contains("控制价")) {
            // 可以存到额外字段，这里暂不处理
            log.debug("忽略最高限价字段: {}", fieldValue);
        } else if (fieldName.contains("项目单位")) {
            // 可以存到额外字段
        } else if (fieldName.contains("采购需求")) {
            if (parsed.getSectionProjectNeed() == null || parsed.getSectionProjectNeed().isEmpty()) {
                parsed.setSectionProjectNeed(fieldValue);
            }
        } else if (fieldName.contains("合同履行期限")) {
            // 可以存到额外字段
        } else if (fieldName.contains("招标方式") || fieldName.contains("采购方式")) {
            if (parsed.getTenderMethod() == null || parsed.getTenderMethod().isEmpty()) {
                parsed.setTenderMethod(fieldValue);
            }
        }
    }

    /**
     * 清理Word风格的项目名称
     */
    private String cleanWordStyleProjectName(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return projectName;
        }

        String cleaned = projectName.trim();

        // 1. 如果包含"项目预算金额"，截断到该位置
        if (cleaned.contains("项目预算金额")) {
            int index = cleaned.indexOf("项目预算金额");
            cleaned = cleaned.substring(0, index).trim();
        }

        // 2. 如果包含数字点和"项目预算金额"类似模式
        if (cleaned.matches(".*\\d+\\..*")) {
            // 移除数字点和后面的内容
            cleaned = cleaned.replaceAll("\\s*\\d+\\..*$", "").trim();
        }

        // 3. 移除常见的后缀
        String[] suffixes = {
            "招标公告", "采购公告", "竞争性谈判公告", "单一来源公告",
            "询价公告", "成交公告", "公示", "通知",
            "项目名称：", "项目名称:", "项目名称",
            "名称：", "名称:", "名称"
        };

        for (String suffix : suffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
            }
        }

        // 4. 清理多余空格
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        log.debug("项目名称清理: {} -> {}", projectName, cleaned);
        return cleaned;
    }

    /**
     * 解析Word文档的其他章节
     */
    private void parseOtherWordSections(Element container, TenderProjectDetailParsed parsed) {
        // 查找所有h2标签
        List<Element> h2Elements = container.select("h2");

        Map<String, String> sections = new LinkedHashMap<>();

        for (int i = 0; i < h2Elements.size(); i++) {
            Element h2 = h2Elements.get(i);
            String sectionTitle = h2.text().trim();

            // 跳过基本情况章节（已经处理过）
            if (sectionTitle.contains("项目基本情况") || sectionTitle.contains("一、")) {
                continue;
            }

            // 获取章节内容（从当前h2到下一个h2之间的所有文本）
            StringBuilder content = new StringBuilder();
            Node currentNode = h2.nextSibling();

            while (currentNode != null) {
                if (currentNode instanceof Element) {
                    Element element = (Element) currentNode;

                    // 如果遇到下一个h2，停止
                    if ("h2".equals(element.tagName())) {
                        break;
                    }

                    // 收集文本内容
                    if (!element.text().trim().isEmpty()) {
                        content.append(element.text().trim()).append("\n");
                    }
                } else if (currentNode instanceof TextNode) {
                    String text = ((TextNode) currentNode).text().trim();
                    if (!text.isEmpty()) {
                        content.append(text).append("\n");
                    }
                }

                currentNode = currentNode.nextSibling();
            }

            String sectionContent = content.toString().trim();
            if (!sectionContent.isEmpty()) {
                sections.put(sectionTitle, sectionContent);
                log.debug("提取Word章节: {} -> 长度: {}", sectionTitle, sectionContent.length());
            }
        }

        // 处理各个章节
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            processWordSection(entry.getKey(), entry.getValue(), parsed);
        }
    }

    /**
     * 处理Word文档的章节
     */
    private void processWordSection(String sectionTitle, String content, TenderProjectDetailParsed parsed) {
        log.debug("处理Word章节: {}", sectionTitle);

        if (sectionTitle.contains("申请人的资格要求") || sectionTitle.contains("资格要求") || sectionTitle.contains("二、")) {
            parsed.setSectionQualification(content);
        }
        else if (sectionTitle.contains("获取招标文件") || sectionTitle.contains("三、")) {
            parsed.setSectionDocAcquisition(content);
            extractDocTimeRangeEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("提交投标文件") || sectionTitle.contains("四、")) {
            parsed.setSectionBiddingSchedule(content);
            extractBiddingInfoEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("公告期限") || sectionTitle.contains("五、")) {
            parsed.setSectionAnnouncementPeriod(content);
        }
        else if (sectionTitle.contains("其他补充事宜") || sectionTitle.contains("六、") || sectionTitle.contains("七、")) {
            parsed.setSectionOtherMatters(content);
        }
        else if (sectionTitle.contains("对本次招标提出询问") || sectionTitle.contains("联系方式") || sectionTitle.contains("八、")) {
            parsed.setSectionContact(content);
            extractContactInfoEnhanced(content, parsed);
        }
        else if (sectionTitle.contains("项目概况")) {
            parsed.setSectionProjectOverview(content);
        }
    }

    /**
     * 验证和修复Word风格字段
     */
    private void validateWordStyleFields(TenderProjectDetailParsed parsed) {
        // 检查预算金额是否需要万元转换
        if (parsed.getBudgetAmount() != null) {
            BigDecimal amount = parsed.getBudgetAmount();

            // 如果金额小于10000，但看起来像是一个大型项目，可能需要重新考虑单位
            // 这里只是记录警告，具体业务逻辑可能需要调整
            if (amount.compareTo(new BigDecimal("10000")) < 0) {
                log.warn("预算金额 {} 元可能过小，请确认是否需要进行万元转换", amount);
            }
        }

        // 确保项目名称没有污染
        if (parsed.getProname() != null) {
            String originalName = parsed.getProname();
            String cleanedName = cleanWordStyleProjectName(originalName);

            if (!originalName.equals(cleanedName)) {
                log.info("修复项目名称: {} -> {}", originalName, cleanedName);
                parsed.setProname(cleanedName);
            }
        }
    }

    /**
     * 从非标准格式页面提取基本信息
     */
    private void extractBasicInfoFromNonStandard(Element element, TenderProjectDetailParsed parsed, String originalProname) {
        // 检查是否是表格结构
        if (element.select("table").size() > 0) {
            log.debug("检测到表格结构，使用表格解析逻辑");
            extractInfoFromTableStructure(element, parsed, originalProname);
            return;
        }

        String fullText = element.text();

        // 优先使用原始项目名称
        if (originalProname != null && !originalProname.isEmpty()) {
            parsed.setProname(originalProname);
            log.debug("使用原始项目名称: {}", originalProname);
        } else {
            // 尝试从非标准格式中提取项目名称
            String extractedName = extractProjectNameFromNonStandardPage(element, fullText);
            if (extractedName != null && !extractedName.isEmpty()) {
                parsed.setProname(extractedName);
            }
        }

        // 尝试提取项目编号
        if (parsed.getProno() == null) {
            Pattern noPattern = Pattern.compile("([A-Za-z0-9\\-]{8,30})");
            Matcher matcher = noPattern.matcher(fullText);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                // 检查是否看起来像项目编号
                if (candidate.matches(".*[A-Z]+.*") || candidate.matches(".*\\d{4,}.*")) {
                    parsed.setProno(candidate);
                }
            }
        }

        // 尝试提取预算金额
        if (parsed.getBudgetAmount() == null) {
            Pattern budgetPattern = Pattern.compile("(?:预算|金额|投资)[：:]\\s*([\\d,.]+)\\s*(?:万元|元|万)?");
            Matcher matcher = budgetPattern.matcher(fullText);
            if (matcher.find()) {
                parsed.setBudgetAmount(parseAmount(matcher.group(1)));
            }
        }
    }

    /**
     * 从表格结构中提取信息
     */
    private void extractInfoFromTableStructure(Element element, TenderProjectDetailParsed parsed, String originalProname) {
        String fullText = element.text();

        // 提取项目名称
        if (originalProname != null && !originalProname.isEmpty()) {
            parsed.setProname(originalProname);
        } else {
            // 从表格中提取项目名称
            Pattern namePattern = Pattern.compile("采购项目名称[：:]\\s*([^\\n]+)");
            Matcher nameMatcher = namePattern.matcher(fullText);
            if (nameMatcher.find()) {
                String projectName = nameMatcher.group(1).trim();
                parsed.setProname(cleanProjectName(projectName));
            }
        }

        // 提取项目编号
        Pattern noPattern = Pattern.compile("采购项目编号[：:]\\s*([A-Za-z0-9\\-]+)");
        Matcher noMatcher = noPattern.matcher(fullText);
        if (noMatcher.find()) {
            parsed.setProno(noMatcher.group(1).trim());
        }

        // 提取预算金额（特别注意万元单位）
        Pattern budgetPattern = Pattern.compile("预算金额[：:]\\s*([^\\n]+)");
        Matcher budgetMatcher = budgetPattern.matcher(fullText);
        if (budgetMatcher.find()) {
            String budgetStr = budgetMatcher.group(1).trim();
            // 这里确保调用修复后的parseAmount方法
            BigDecimal amount = parseAmount(budgetStr);
            if (amount != null) {
                parsed.setBudgetAmount(amount);
                log.info("表格结构提取预算金额: {} -> {}", budgetStr, amount);
            } else {
                log.warn("表格结构预算金额解析失败: {}", budgetStr);
            }
        }

        // 提取采购方式
        Pattern methodPattern = Pattern.compile("采购方式[：:]\\s*([^\\n]+)");
        Matcher methodMatcher = methodPattern.matcher(fullText);
        if (methodMatcher.find()) {
            parsed.setTenderMethod(methodMatcher.group(1).trim());
        }

        // 提取采购人信息
        Pattern purchaserPattern = Pattern.compile("采购人名称[：:]\\s*([^\\n]+)");
        Matcher purchaserMatcher = purchaserPattern.matcher(fullText);
        if (purchaserMatcher.find()) {
            parsed.setPurchaser(purchaserMatcher.group(1).trim());
        }

        // 提取代理机构信息
        Pattern agentPattern = Pattern.compile("采购代理机构全称[：:]\\s*([^\\n]+)");
        Matcher agentMatcher = agentPattern.matcher(fullText);
        if (agentMatcher.find()) {
            parsed.setAgentCompany(agentMatcher.group(1).trim());
        }

        // 提取开标时间
        Pattern openTimePattern = Pattern.compile("开标时间[：:]\\s*([^\\n]+)");
        Matcher openTimeMatcher = openTimePattern.matcher(fullText);
        if (openTimeMatcher.find()) {
            String timeStr = openTimeMatcher.group(1).trim();
            parsed.setKaibiaodate(parseDateTime(timeStr));
        }

        // 提取开标地点
        Pattern placePattern = Pattern.compile("开标地点[：:]\\s*([^\\n]+)");
        Matcher placeMatcher = placePattern.matcher(fullText);
        if (placeMatcher.find()) {
            parsed.setChangdi(placeMatcher.group(1).trim());
        }
    }

    /**
     * 从非标准格式页面提取项目名称
     */
    private String extractProjectNameFromNonStandardPage(Element element, String fullText) {
        String projectName = null;

        // 方法1：从标题标签中提取
        Element titleElement = element.selectFirst("h1, h2, h3, strong");
        if (titleElement != null) {
            String title = titleElement.text().trim();
            if (title.length() > 5 && title.length() < 200) {
                projectName = cleanProjectName(title);
                if (isValidProjectName(projectName)) {
                    return projectName;
                }
            }
        }

        // 方法2：从正文开头提取（通常项目名称在开头）
        String[] sentences = fullText.split("。|！|!|；|;");
        if (sentences.length > 0) {
            String firstSentence = sentences[0].trim();
            if (firstSentence.length() > 10 && firstSentence.length() < 100) {
                projectName = cleanProjectName(firstSentence);
                if (isValidProjectName(projectName)) {
                    return projectName;
                }
            }
        }

        // 方法3：查找包含"项目"的句子
        for (String sentence : fullText.split("。|！|!|；|;|，|,")) {
            if (sentence.contains("项目") && sentence.length() > 5 && sentence.length() < 80) {
                projectName = cleanProjectName(sentence);
                if (isValidProjectName(projectName)) {
                    return projectName;
                }
            }
        }

        return null;
    }

    /**
     * 清理项目名称，移除多余信息
     */
    private String cleanProjectName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        // 移除常见的后缀
        name = name.replaceAll("招标公告$", "")
            .replaceAll("采购公告$", "")
            .replaceAll("竞争性谈判公告$", "")
            .replaceAll("单一来源公告$", "")
            .replaceAll("询价公告$", "")
            .replaceAll("成交公告$", "")
            .replaceAll("公示$", "")
            .replaceAll("通知$", "")
            .replaceAll("^公告[：:]\\s*", "")
            .replaceAll("^项目名称[：:]\\s*", "")
            .replaceAll("^项目[：:]\\s*", "")
            .replaceAll("发布时间.*$", "")
            .replaceAll("\\d{4}-\\d{2}-\\d{2}.*$", "")
            .replaceAll("采购项目$", "")
            .trim();

        // 如果以"的"结尾，且长度合适，保留
        if (name.endsWith("的") && name.length() < 10) {
            name = name.substring(0, name.length() - 1);
        }

        return name;
    }

    /**
     * 验证项目名称是否有效
     */
    private boolean isValidProjectName(String name) {
        if (name == null || name.isEmpty() || name.length() < 3 || name.length() > 100) {
            return false;
        }

        // 排除不合适的名称
        if (name.contains("出让标的") ||
            name.contains("基本情况") ||
            name.contains("发布时间") ||
            name.contains("公告期限") ||
            name.contains("联系方式") ||
            name.contains("采购人") ||
            name.contains("代理机构")) {
            return false;
        }

        // 应该包含一些中文字符
        return name.matches(".*[\\u4e00-\\u9fa5]+.*");
    }

    /**
     * 从URL中提取项目名称（备用）
     */
    private String extractProjectNameFromUrl(String url) {
        try {
            // 尝试从URL参数中提取
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query != null) {
                // 解析查询参数
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.contains("title") || param.contains("name") || param.contains("proname")) {
                        String[] kv = param.split("=");
                        if (kv.length == 2) {
                            return URLDecoder.decode(kv[1], "UTF-8");
                        }
                    }
                }
            }

            // 从路径中提取
            String path = uri.getPath();
            if (path != null) {
                // 移除文件扩展名和数字ID
                String name = path.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", " ")
                    .replaceAll("\\d+", " ")
                    .trim();
                if (name.length() > 5) {
                    return name;
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }

        return "未知项目-" + System.currentTimeMillis();
    }

    /**
     * 判断是否为标准格式招标文件
     * 标准格式特征：
     * 1. 有标准的章节标题（一、项目基本情况 二、申请人资格要求 等）
     * 2. 包含项目编号、预算金额等关键字段
     * 3. 不是单一来源采购、竞争性谈判、询价等非标准格式
     * 4. 不是表格结构（表格结构通常是简易公告）
     */
    private boolean isStandardTenderPage(Element ewbCopyDiv) {
        String fullText = ewbCopyDiv.text();

        // 1. 检查是否是表格结构（非标准格式）
        boolean hasTableStructure = ewbCopyDiv.select("table").size() > 0;
        if (hasTableStructure) {
            log.debug("检测到表格结构，可能为非标准格式");
            // 表格结构且没有标准章节标题，通常是非标准格式
            return false;
        }

        // 2. 必须包含的关键字段（标准招标文件一定有这些）
        boolean hasRequiredFields = fullText.contains("项目编号：") &&
            fullText.contains("项目名称：") &&
            fullText.contains("预算金额：");

        // 3. 必须有标准的章节结构（至少3个标准章节）
        boolean hasStandardSections = hasStandardSectionStructure(ewbCopyDiv);

        // 4. 排除非标准格式（单一来源、竞争性谈判、询价等）
        boolean isNonStandardFormat = isNonStandardFormat(fullText);

        log.debug("页面格式检测 - 必需字段: {}, 标准章节: {}, 非标准格式: {}, 表格结构: {}",
            hasRequiredFields, hasStandardSections, isNonStandardFormat, hasTableStructure);

        // 是标准格式当且仅当：包含必需字段 AND 有标准章节结构 AND 不是非标准格式
        return hasRequiredFields && hasStandardSections && !isNonStandardFormat;
    }

    /**
     * 检查是否有标准章节结构
     */
    private boolean hasStandardSectionStructure(Element ewbCopyDiv) {
        int standardSectionCount = 0;

        // 检查标准章节标题
        String[] standardSections = {
            "项目概况",
            "一、项目基本情况", "项目基本情况",
            "二、申请人资格要求", "申请人资格要求", "资格要求",
            "三、获取招标文件", "获取招标文件",
            "四、提交投标文件截止时间、开标时间和地点", "提交投标文件截止时间",
            "五、公告期限", "公告期限",
            "六、其他补充事宜", "其他补充事宜",
            "七、对本次招标提出询问", "联系方式"
        };

        String fullText = ewbCopyDiv.text();
        for (String section : standardSections) {
            if (fullText.contains(section)) {
                standardSectionCount++;
            }
        }

        // 同时检查strong标签数量（标准格式通常有多个章节标题）
        int strongCount = ewbCopyDiv.select("strong").size();

        log.debug("章节结构检测 - 标准章节数: {}, strong标签数: {}", standardSectionCount, strongCount);

        // 满足以下任一条件即认为有标准章节结构：
        // 1. 至少有3个标准章节
        // 2. 至少有4个strong标签（通常是章节标题）
        // 3. 有明确的"一、项目基本情况"这样的编号章节
        return standardSectionCount >= 3 ||
            strongCount >= 4 ||
            fullText.contains("一、项目基本情况");
    }

    /**
     * 判断是否为非标准格式（单一来源、竞争性谈判、询价等）
     */
    private boolean isNonStandardFormat(String fullText) {
        // 排除单一来源采购
        if (fullText.contains("单一来源采购") ||
            fullText.contains("单一来源公告") ||
            fullText.contains("采用单一来源采购方式原因及相关说明") ||
            fullText.contains("拟定供应商信息") ||
            fullText.contains("单一来源方式进行采购")) {
            return true;
        }

        // 排除竞争性谈判（除非它是标准招标格式）
        if (fullText.contains("竞争性谈判公告") &&
            !fullText.contains("一、项目基本情况") &&
            !fullText.contains("项目编号：")) {
            return true;
        }

        // 排除询价采购
        if (fullText.contains("询价采购") ||
            fullText.contains("询价公告")) {
            return true;
        }

        // 排除谈判公告
        if (fullText.contains("谈判公告") &&
            !fullText.contains("一、项目基本情况")) {
            return true;
        }

        // 排除磋商公告
        if (fullText.contains("磋商公告") &&
            !fullText.contains("项目编号：")) {
            return true;
        }

        // 排除框架协议
        if (fullText.contains("框架协议") ||
            fullText.contains("征集公告")) {
            return true;
        }

        return false;
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
        else if (sectionTitle.contains("项目编号") && !sectionTitle.contains("项目基本情况")) {
            // 单独处理项目编号行
            parseProjectNoLine(content, parsed);
        }
        else if (sectionTitle.contains("预算金额") && !sectionTitle.contains("项目基本情况")) {
            // 单独处理预算金额行
            parseBudgetAmountLine(content, parsed);
        }
    }

    /**
     * 单独处理项目编号行
     */
    private void parseProjectNoLine(String content, TenderProjectDetailParsed parsed) {
        if (parsed.getProno() == null || parsed.getProno().isEmpty()) {
            Pattern pattern = Pattern.compile("项目编号[：:]\\s*([A-Za-z0-9\\-]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String projectNo = matcher.group(1).trim();
                if (projectNo.length() > 5 && projectNo.length() < 50) {
                    parsed.setProno(projectNo);
                    log.debug("单独提取项目编号: {}", projectNo);
                }
            }
        }
    }

    /**
     * 单独处理预算金额行
     */
    private void parseBudgetAmountLine(String content, TenderProjectDetailParsed parsed) {
        if (parsed.getBudgetAmount() == null) {
            Pattern pattern = Pattern.compile("预算金额[：:]\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String budgetStr = matcher.group(1).trim();
                BigDecimal amount = parseAmount(budgetStr);
                if (amount != null) {
                    parsed.setBudgetAmount(amount);
                    log.debug("单独提取预算金额: {} -> {}", budgetStr, amount);
                }
            }
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
                String cleanValue = cleanWordStyleProjectName(value);
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
                location = cleanLocationInfo(location);
                parsed.setChangdi(location);
            }
        }
    }

    /**
     * 清理地点信息
     */
    private String cleanLocationInfo(String location) {
        if (location == null || location.isEmpty()) {
            return location;
        }

        // 1. 移除技术支持电话等无关信息
        location = location.replaceAll("技术支持电话：.*", "")
            .replaceAll("电话：.*", "")
            .replaceAll("\\d{3}-\\d{4}-\\d{4}", "")
            .trim();

        // 2. 如果包含"。"，只取第一个句号前的内容
        if (location.contains("。")) {
            location = location.split("。")[0].trim();
        }

        // 3. 如果太长（超过200字），截断
        if (location.length() > 200) {
            location = location.substring(0, 200);
        }

        return location;
    }

    /**
     * 提取联系信息（使用正则表达式）- 修复版
     */
    private void extractContactInfoEnhanced(String content, TenderProjectDetailParsed parsed) {
        log.debug("提取联系信息（正则）: {}", content);

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        // 清理内容
        content = content.replaceAll("\\s+", " ").trim();
        content = content.replaceAll("　", " "); // 替换全角空格

        // 1. 提取采购人信息
        extractByRegex(content, "采购人信息[\\s\\S]*?名称[：:]\\s*([^\\n]+?)\\s*(?=(地址|联系方式|$))", value -> {
            if (value.length() > 100) {
                value = value.substring(0, 100);
            }
            parsed.setPurchaser(value);
        });

        extractByRegex(content, "采购人信息[\\s\\S]*?地址[：:]\\s*([^\\n]+?)\\s*(?=(联系方式|电话|$))", value -> {
            if (value.length() > 200) {
                value = value.substring(0, 200);
            }
            parsed.setPurchaserAddress(value);
        });

        extractByRegex(content, "采购人信息[\\s\\S]*?(?:联系方式|电话)[：:]\\s*([\\d-]+)", parsed::setPurchaserPhone);

        // 2. 提取采购代理机构信息
        extractByRegex(content, "采购代理机构信息[\\s\\S]*?名称[：:]\\s*([^\\n]+?)\\s*(?=(地址|联系方式|$))", value -> {
            if (value.length() > 100) {
                value = value.substring(0, 100);
            }
            parsed.setAgentCompany(value);
        });

        extractByRegex(content, "采购代理机构信息[\\s\\S]*?地址[：:]\\s*([^\\n]+?)\\s*(?=(联系方式|电话|$))", value -> {
            if (value.length() > 200) {
                value = value.substring(0, 200);
            }
            parsed.setAgentAddress(value);
        });

        extractByRegex(content, "采购代理机构信息[\\s\\S]*?(?:联系方式|电话)[：:]\\s*([\\d-]+)", parsed::setAgentPhone);

        // 3. 提取项目联系方式
        extractByRegex(content, "项目联系方式[\\s\\S]*?项目联系人[：:]\\s*([^\\n]+?)\\s*(?=(电话|$))", value -> {
            if (value.length() > 50) {
                value = value.substring(0, 50);
            }
            parsed.setProjectContact(value);
        });

        extractByRegex(content, "项目联系方式[\\s\\S]*?电话[：:]\\s*([\\d-]+)", parsed::setProjectPhone);
    }

    /**
     * 使用正则表达式提取字段（带长度限制）
     */
    private void extractByRegex(String content, String patternStr, java.util.function.Consumer<String> setter) {
        try {
            Pattern regex = Pattern.compile(patternStr, Pattern.DOTALL);
            Matcher matcher = regex.matcher(content);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.isEmpty()) {
                    // 清理换行符和多余空格
                    value = value.replaceAll("\\s+", " ").trim();
                    setter.accept(value);
                    log.debug("正则提取: {} -> {}", patternStr, value);
                }
            }
        } catch (Exception e) {
            log.warn("正则提取失败: {}, {}", patternStr, e.getMessage());
        }
    }

    /**
     * 从Word文档中解析联系信息
     */
    private void parseContactInfoFromWord(Element container, TenderProjectDetailParsed parsed) {
        // 查找"八、对本次招标提出询问"部分
        Element contactSection = null;
        for (Element element : container.select("h2, h3")) {
            if (element.text().contains("对本次招标提出询问") || element.text().contains("联系方式") || element.text().contains("八、")) {
                contactSection = element;
                break;
            }
        }

        if (contactSection == null) {
            log.warn("未找到联系信息章节");
            return;
        }

        // 收集后续所有p标签，直到遇到下一个h2或结束
        List<Element> contactParagraphs = new ArrayList<>();
        Node currentNode = contactSection.nextSibling();

        while (currentNode != null) {
            if (currentNode instanceof Element) {
                Element element = (Element) currentNode;

                // 如果遇到下一个h2，停止
                if (element.tagName().startsWith("h")) {
                    break;
                }

                // 收集p标签
                if ("p".equals(element.tagName())) {
                    contactParagraphs.add(element);
                }
            }

            currentNode = currentNode.nextSibling();
        }

        log.debug("找到联系信息段落数: {}", contactParagraphs.size());

        // 解析每个段落
        String currentContactType = "";
        for (Element pElement : contactParagraphs) {
            String text = pElement.text().trim();

            if (text.contains("采购人信息")) {
                currentContactType = "purchaser";
            } else if (text.contains("采购代理机构信息")) {
                currentContactType = "agent";
            } else if (text.contains("项目联系方式")) {
                currentContactType = "project";
            } else if (text.contains("名称：") || text.contains("名 称：")) {
                parseContactDetail(text, "名称", currentContactType, parsed);
            } else if (text.contains("地址：") || text.contains("地 址：")) {
                parseContactDetail(text, "地址", currentContactType, parsed);
            } else if (text.contains("联系方式：")) {
                parseContactDetail(text, "联系方式", currentContactType, parsed);
            } else if (text.contains("项目联系人：")) {
                parseContactDetail(text, "项目联系人", "project", parsed);
            } else if (text.contains("电话：") || text.contains("电 话：")) {
                parseContactDetail(text, "电话", currentContactType, parsed);
            }
        }
    }

    /**
     * 解析联系信息详情
     */
    private void parseContactDetail(String text, String fieldType, String contactType, TenderProjectDetailParsed parsed) {
        // 清理文本：将全角冒号替换为半角
        text = text.replaceAll("：", ":");

        String[] parts = text.split(":", 2);
        if (parts.length == 2) {
            String value = parts[1].trim();

            switch (contactType) {
                case "purchaser":
                    if ("名称".equals(fieldType)) {
                        if (value.length() > 100) value = value.substring(0, 100);
                        parsed.setPurchaser(value);
                    } else if ("地址".equals(fieldType)) {
                        if (value.length() > 200) value = value.substring(0, 200);
                        parsed.setPurchaserAddress(value);
                    } else if ("联系方式".equals(fieldType)) {
                        parsed.setPurchaserPhone(value);
                    }
                    break;

                case "agent":
                    if ("名称".equals(fieldType)) {
                        if (value.length() > 100) value = value.substring(0, 100);
                        parsed.setAgentCompany(value);
                    } else if ("地址".equals(fieldType)) {
                        if (value.length() > 200) value = value.substring(0, 200);
                        parsed.setAgentAddress(value);
                    } else if ("联系方式".equals(fieldType)) {
                        parsed.setAgentPhone(value);
                    }
                    break;

                case "project":
                    if ("项目联系人".equals(fieldType)) {
                        if (value.length() > 50) value = value.substring(0, 50);
                        parsed.setProjectContact(value);
                    } else if ("电话".equals(fieldType)) {
                        parsed.setProjectPhone(value);
                    }
                    break;
            }

            log.debug("解析联系信息: {}-{} -> {}", contactType, fieldType, value);
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
            Pattern pattern = Pattern.compile("采购人信息[\\s\\S]*?名称[：:]\\s*([^\\n]+?)\\s*(?=(地址|联系方式|$))");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (value.length() > 100) value = value.substring(0, 100);
                parsed.setPurchaser(value);
            }
        }

        if (parsed.getAgentCompany() == null || parsed.getAgentCompany().isEmpty()) {
            Pattern pattern = Pattern.compile("采购代理机构信息[\\s\\S]*?名称[：:]\\s*([^\\n]+?)\\s*(?=(地址|联系方式|$))");
            Matcher matcher = pattern.matcher(fullText);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (value.length() > 100) value = value.substring(0, 100);
                parsed.setAgentCompany(value);
            }
        }

        if (parsed.getProjectPhone() == null || parsed.getProjectPhone().isEmpty() ||
            parsed.getProjectPhone().equals("0512-58188537")) {
            Pattern pattern = Pattern.compile("项目联系方式[\\s\\S]*?电话[：:]\\s*([\\d-]+)");
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

    private String fetchHtml(String url) throws Exception {
        CloseableHttpClient client = HebeiHttpClientCommonFactory.getClient();
        HttpGet request = new HttpGet(url);
        String token = HebeiTokenCommonManager.getToken();
        if (token != null && !token.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + token);
        }

        // 模拟浏览器请求头
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        request.setHeader("Accept-Encoding", "gzip, deflate");
        request.setHeader("Connection", "keep-alive");
        request.setHeader("Upgrade-Insecure-Requests", "1");
        request.setHeader("Cache-Control", "max-age=0");
        request.setHeader("Referer", "http://ssl.hebpr.cn/hbggfwpt/jydt/salesPlat.html");

        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } else {
                log.error("获取页面失败，状态码: {}, URL: {}", statusCode, url);
                return null;
            }
        } catch (Exception e) {
            log.error("获取页面异常，URL: {}", url, e);
            throw e;
        }
    }

    /**
     * 解析金额字符串，支持万元单位转换
     * 支持格式: "100万元"、"100万"、"100,000.00元"、"100.5万"、"100w"等
     * @return BigDecimal 类型，保留精确计算能力
     */
    private BigDecimal parseAmount(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            String originalText = text.trim();
            log.debug("解析金额字符串: {}", originalText);

            // 方案1：尝试匹配"预算金额：200万元"这种完整格式
            Pattern fullPattern = Pattern.compile("预算金额[：:]\\s*([\\d,.]+)\\s*(万|万元|w|W)");
            Matcher fullMatcher = fullPattern.matcher(originalText);
            if (fullMatcher.find()) {
                String numStr = fullMatcher.group(1).replaceAll(",", "");
                BigDecimal amount = new BigDecimal(numStr).multiply(new BigDecimal("10000"));
                log.info("完整格式万元转换: {} -> {}元", originalText, amount);
                return amount.setScale(2, RoundingMode.HALF_UP);
            }

            // 方案2：尝试匹配"200万元"这种简单格式
            Pattern simplePattern = Pattern.compile("([\\d,.]+)\\s*(万|万元|w|W|万千瓦|万千瓦时)");
            Matcher simpleMatcher = simplePattern.matcher(originalText);
            if (simpleMatcher.find()) {
                String numStr = simpleMatcher.group(1).replaceAll(",", "");
                BigDecimal amount = new BigDecimal(numStr).multiply(new BigDecimal("10000"));
                log.info("简单格式万元转换: {} -> {}元", originalText, amount);
                return amount.setScale(2, RoundingMode.HALF_UP);
            }

            // 方案3：处理"200元"或纯数字
            Pattern yuanPattern = Pattern.compile("([\\d,.]+)\\s*(元|圆|RMB|￥|¥)?");
            Matcher yuanMatcher = yuanPattern.matcher(originalText);
            if (yuanMatcher.find()) {
                String numStr = yuanMatcher.group(1).replaceAll(",", "");
                BigDecimal amount = new BigDecimal(numStr);

                // 如果是纯数字且小于10000，但原始文本包含"万"字，尝试再次检查
                if (amount.compareTo(new BigDecimal("10000")) < 0 &&
                    originalText.contains("万")) {
                    // 可能是单位识别问题，强制转换
                    amount = amount.multiply(new BigDecimal("10000"));
                    log.warn("强制万元转换（疑似单位识别问题）: {} -> {}元", originalText, amount);
                }

                log.info("元格式解析: {} -> {}元", originalText, amount);
                return amount.setScale(2, RoundingMode.HALF_UP);
            }

            log.warn("无法解析金额字符串: {}", originalText);
        } catch (Exception e) {
            log.warn("解析金额失败：{}，错误：{}", text, e.getMessage());
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

    /**
     * 验证和修复解析结果
     */
    private void validateAndFixParsedData(TenderProjectDetailParsed parsed, String originalProname) {
        // 1. 如果项目名称为空，使用原始项目名称
        if ((parsed.getProname() == null || parsed.getProname().isEmpty())
            && originalProname != null && !originalProname.isEmpty()) {
            parsed.setProname(originalProname);
            log.debug("解析后项目名称为空，使用原始项目名称: {}", originalProname);
        }

        // 2. 验证项目名称是否被污染
        if (parsed.getProname() != null) {
            String originalName = parsed.getProname();
            String cleanedName = cleanWordStyleProjectName(originalName);

            if (!originalName.equals(cleanedName)) {
                log.info("修复项目名称: {} -> {}", originalName, cleanedName);
                parsed.setProname(cleanedName);
            }
        }

        // 3. 验证预算金额
        if (parsed.getBudgetAmount() != null) {
            BigDecimal amount = parsed.getBudgetAmount();

            // 检查是否可能忘了万元转换（金额过小）
            if (amount.compareTo(new BigDecimal("10000")) < 0) {
                log.warn("预算金额 {} 元过小，可能需要进行万元转换", amount);
                // 这里可以记录到日志或触发人工检查
            }
        }

        // 4. 验证字段长度，防止数据库插入失败
        validateFieldLength(parsed);

        // 5. 验证开标地点长度
        if (parsed.getChangdi() != null && parsed.getChangdi().length() > 500) {
            String location = parsed.getChangdi().substring(0, 500);
            log.info("开标地点过长，截断到500字符");
            parsed.setChangdi(location);
        }
    }

    /**
     * 验证字段长度，防止数据库插入失败
     */
    private void validateFieldLength(TenderProjectDetailParsed parsed) {
        // 采购人名称长度限制
        if (parsed.getPurchaser() != null && parsed.getPurchaser().length() > 100) {
            log.warn("采购人名称过长，截断到100字符: {}", parsed.getPurchaser());
            parsed.setPurchaser(parsed.getPurchaser().substring(0, 100));
        }

        // 采购人地址长度限制
        if (parsed.getPurchaserAddress() != null && parsed.getPurchaserAddress().length() > 200) {
            log.warn("采购人地址过长，截断到200字符: {}", parsed.getPurchaserAddress());
            parsed.setPurchaserAddress(parsed.getPurchaserAddress().substring(0, 200));
        }

        // 代理机构名称长度限制
        if (parsed.getAgentCompany() != null && parsed.getAgentCompany().length() > 100) {
            log.warn("代理机构名称过长，截断到100字符: {}", parsed.getAgentCompany());
            parsed.setAgentCompany(parsed.getAgentCompany().substring(0, 100));
        }

        // 代理机构地址长度限制
        if (parsed.getAgentAddress() != null && parsed.getAgentAddress().length() > 200) {
            log.warn("代理机构地址过长，截断到200字符: {}", parsed.getAgentAddress());
            parsed.setAgentAddress(parsed.getAgentAddress().substring(0, 200));
        }

        // 项目联系人长度限制
        if (parsed.getProjectContact() != null && parsed.getProjectContact().length() > 50) {
            log.warn("项目联系人过长，截断到50字符: {}", parsed.getProjectContact());
            parsed.setProjectContact(parsed.getProjectContact().substring(0, 50));
        }

        // 项目名称长度限制
        if (parsed.getProname() != null && parsed.getProname().length() > 200) {
            log.warn("项目名称过长，截断到200字符: {}", parsed.getProname());
            parsed.setProname(parsed.getProname().substring(0, 200));
        }

        // 采购方式长度限制
        if (parsed.getTenderMethod() != null && parsed.getTenderMethod().length() > 50) {
            log.warn("采购方式过长，截断到50字符: {}", parsed.getTenderMethod());
            parsed.setTenderMethod(parsed.getTenderMethod().substring(0, 50));
        }

        // 地区长度限制
        if (parsed.getArea() != null && parsed.getArea().length() > 50) {
            log.warn("地区过长，截断到50字符: {}", parsed.getArea());
            parsed.setArea(parsed.getArea().substring(0, 50));
        }
    }

    /**
     * 清理字段值
     */
    private String cleanFieldValue(String value) {
        if (value == null) return "";

        // 移除常见的后缀
        String cleaned = value.replaceAll("，.*$", "")
            .replaceAll("\\s+", " ")
            .trim();

        return cleaned;
    }
}
