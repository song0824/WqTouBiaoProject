package org.dromara.toubiao.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 招标项目解析详情 Service
 */
@Service
public interface TenderProjectDetailParsedService {

    /**
     * 查询所有数据
     * @return 所有解析详情列表
     */
    List<TenderProjectDetailParsed> getAll();

    /**
     * 分页查询
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param position 职位名称，可为空
     * @param title 标题，可为空
     * @return 分页结果
     */
    IPage<TenderProjectDetailParsed> getPage(Integer pageNum, Integer pageSize, String position, String title,String code);

    /**
     * 根据ID查询
     * @param id 主键ID
     * @return 解析详情
     */
    TenderProjectDetailParsed getById(Integer id);


}
