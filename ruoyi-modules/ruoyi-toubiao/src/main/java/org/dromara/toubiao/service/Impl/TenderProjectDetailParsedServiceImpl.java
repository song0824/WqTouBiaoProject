package org.dromara.toubiao.service.Impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.dromara.toubiao.mapper.TenderProjectDetailParsedMapper;
import org.dromara.toubiao.service.TenderProjectDetailParsedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 招标项目解析详情 Service实现
 */
@Service
public class TenderProjectDetailParsedServiceImpl implements TenderProjectDetailParsedService {

    @Autowired
    private TenderProjectDetailParsedMapper tenderProjectDetailParsedMapper;

    /**
     * 查询所有数据
     * @return 所有解析详情列表
     */
    @Override
    public List<TenderProjectDetailParsed> getAll() {
        return tenderProjectDetailParsedMapper.selectAll();
    }

    /**
     * 分页查询
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @Override
    public IPage<TenderProjectDetailParsed> getPage(Integer pageNum, Integer pageSize) {
        Page<TenderProjectDetailParsed> page = new Page<>(pageNum, pageSize);
        return tenderProjectDetailParsedMapper.selectPageList(page);
    }

    /**
     * 根据ID查询
     * @param id 主键ID
     * @return 解析详情
     */
    @Override
    public TenderProjectDetailParsed getById(Integer id) {
        return tenderProjectDetailParsedMapper.selectById(id);
    }


}
