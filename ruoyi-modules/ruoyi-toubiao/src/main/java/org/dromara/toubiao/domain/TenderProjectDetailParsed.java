package org.dromara.toubiao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 招标项目解析详情实体类
 * 对应数据库表：tender_project_detail_parsed
 *
 * @author
 * @date 2025-12-30
 */
@Data
@Component
@TableName("tender_project_detail_parsed")
public class TenderProjectDetailParsed implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 主键 ====================
    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 项目唯一标识（关联tender_project_detail表）
     */
    private String infoid;

    /**
     * 详情页URL
     */
    private String infoUrl;

    // ==================== 项目基本信息 ====================
    /**
     * 项目编号
     */
    private String prono;

    /**
     * 项目名称
     */
    private String proname;

    /**
     * 预算金额（元）
     */
    private BigDecimal budgetAmount;

    /**
     * 采购方式（公开招标/邀请招标等）
     */
    private String tenderMethod;

    /**
     * 项目所属地区
     */
    private String area;

    // ==================== 时间节点 ====================
    /**
     * 公告发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 获取招标文件开始时间
     */
    private LocalDateTime docStartTime;

    /**
     * 获取招标文件结束时间
     */
    private LocalDateTime docEndTime;

    /**
     * 投标截止时间
     */
    private LocalDateTime biddingDeadline;

    /**
     * 开标日期时间
     */
    private LocalDateTime kaibiaodate;

    /**
     * 开标场地
     */
    private String changdi;

    // ==================== 采购人信息 ====================
    /**
     * 采购人名称
     */
    private String purchaser;

    /**
     * 采购人地址
     */
    private String purchaserAddress;

    /**
     * 采购人联系电话
     */
    private String purchaserPhone;

    // ==================== 采购代理机构信息 ====================
    /**
     * 采购代理机构名称
     */
    private String agentCompany;

    /**
     * 采购代理机构地址
     */
    private String agentAddress;

    /**
     * 采购代理机构联系电话
     */
    private String agentPhone;

    // ==================== 项目联系方式 ====================
    /**
     * 项目联系人
     */
    private String projectContact;

    /**
     * 项目联系电话
     */
    private String projectPhone;

    // ==================== 后续AI可能需要的部分 ====================
    /**
     * 采购需求
     */
    private String sectionProjectNeed;

    /**
     * 项目概况
     */
    private String sectionProjectOverview;

    // ==================== 大文本字段（按页面章节分块） ====================
    /**
     * 一、项目基本情况
     */
    private String sectionBasicInfo;

    /**
     * 二、申请人资格要求
     */
    private String sectionQualification;

    /**
     * 三、获取招标文件
     */
    private String sectionDocAcquisition;

    /**
     * 四、提交投标文件截止时间、开标时间和地点
     */
    private String sectionBiddingSchedule;

    /**
     * 五、公告期限
     */
    private String sectionAnnouncementPeriod;

    /**
     * 六、其他补充事宜
     */
    private String sectionOtherMatters;

    /**
     * 七、对本次招标提出询问，请按以下方式联系
     */
    private String sectionContact;

    // ==================== 状态管理 ====================
    /**
     * 解析状态：0-未解析 1-解析中 2-解析成功 3-解析失败
     */
    private Integer parseStatus;

    /**
     * 解析时间
     */
    private LocalDateTime parseTime;

    /**
     * 解析重试次数
     */
    private Integer parseRetryCount;

    /**
     * 解析失败原因
     */
    private String parseErrorMsg;

    // ==================== AI预留字段 ====================
    /**
     * 是否已AI分类：0-否 1-是
     */
    private Integer isAiClassified;

    /**
     * AI分类时间
     */
    private LocalDateTime aiClassifyTime;

    // ==================== 时间戳 ====================
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
