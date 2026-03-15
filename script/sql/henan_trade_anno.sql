-- ----------------------------
-- 河南省公共资源交易公告表（采购公告 projectType=2）
-- ----------------------------
create table if not exists henan_trade_anno (
    db_id              bigint(20)      not null auto_increment    comment '主键',
    id                 varchar(64)     default ''                 comment '公告ID（接口主键，用于拼详情链接）',
    anno_type          varchar(32)     default ''                 comment '公告类型',
    title              varchar(512)    default ''                 comment '标题',
    content            text            default null               comment '内容',
    trade_region       varchar(64)     default ''                 comment '交易地区',
    trade_region_code  varchar(32)     default ''                 comment '交易地区编码',
    project_name       varchar(256)    default null               comment '项目名称',
    project_type       varchar(16)     default ''                 comment '项目类型',
    project_code       varchar(128)    default null               comment '项目编号',
    create_time        varchar(32)     default ''                 comment '公告日期（接口createTime）',
    info_url           varchar(512)    default ''                 comment '原文详情页完整URL',
    primary key (db_id),
    key idx_id (id),
    key idx_create_time (create_time)
) engine=innodb comment = '河南省公共资源交易公告表';
