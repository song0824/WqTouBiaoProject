package org.dromara.toubiao.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.toubiao.domain.TenderProjectDetailParsed;
import org.dromara.toubiao.service.TenderProjectDetailParsedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 招标项目解析详情查询接口
 */
@RestController
@RequestMapping("/api/tender-parsed")
@Slf4j
public class TenderProjectDetailParsedController {

    @Autowired
    private TenderProjectDetailParsedService tenderProjectDetailParsedService;

    /**
     * 1. 查询所有数据
     * GET /api/tender-parsed/all
     */
   // @SaIgnore
    @GetMapping("/all")
    public R<List<TenderProjectDetailParsed>> getAll() {
        try {
            List<TenderProjectDetailParsed> list = tenderProjectDetailParsedService.getAll();
            return R.ok(list);
        } catch (Exception e) {
            log.error("查询所有数据失败", e);
            return R.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 2. 分页查询
     * GET /api/tender-parsed/page
     *
     * @param pageNum 当前页码，默认为1
     * @param pageSize 每页大小，默认为10
     * @param position 职位名称，可为空
     * @param title 标题，可为空
     * @param code 分类代码，可为空
     */
    //@SaIgnore
    @GetMapping("/page")
    public R<Map<String, Object>> getPage(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String position,
        @RequestParam(required = false) String title ,
        @RequestParam(required = false) String code){
        try {
            IPage<TenderProjectDetailParsed> page = tenderProjectDetailParsedService.getPage(pageNum, pageSize, position, title, code);

            Map<String, Object> result = new HashMap<>();
            result.put("current", page.getCurrent());
            result.put("size", page.getSize());
            result.put("total", page.getTotal());
            result.put("pages", page.getPages());
            result.put("records", page.getRecords());

            return R.ok(result);
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return R.fail("分页查询失败: " + e.getMessage());
        }
    }

    /**
     * 3. 根据ID查询
     * GET /api/tender-parsed/{id}
     *
     * @param id 主键ID
     */
    //@SaIgnore
    @GetMapping("/{id}")
    public R<TenderProjectDetailParsed> getById(@PathVariable Integer id) {
        try {
            TenderProjectDetailParsed parsed = tenderProjectDetailParsedService.getById(id);
            if (parsed == null) {
                return R.fail("未找到ID为 " + id + " 的记录");
            }
            return R.ok(parsed);
        } catch (Exception e) {
            log.error("根据ID查询失败: {}", id, e);
            return R.fail("查询失败: " + e.getMessage());
        }
    }


}
