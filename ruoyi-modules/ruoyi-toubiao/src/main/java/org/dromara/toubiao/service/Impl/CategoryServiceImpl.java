package org.dromara.toubiao.service.Impl;

import org.dromara.toubiao.domain.CategoryMessage;
import org.dromara.toubiao.domain.CategoryMessageDTO;
import org.dromara.toubiao.mapper.TenderProjectDetailParsedMapper;
import org.dromara.toubiao.service.CategoryService;
import org.dromara.toubiao.service.CategoryUpdateService;
import org.dromara.toubiao.utils.AiCategory.CozeApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    CozeApiClient cozeApiClient;

    @Autowired
    TenderProjectDetailParsedMapper tenderProjectDetailParsedMapper;

    @Autowired
    CategoryUpdateService categoryUpdateService;

    // 最大并发数：同时发10个API请求
    private static final int MAX_CONCURRENT = 10;

    // 线程池配置
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
        MAX_CONCURRENT,
        MAX_CONCURRENT,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(500),
        new ThreadFactory() {
            private final AtomicInteger threadNum = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "ai-category-thread-" + threadNum.getAndIncrement());
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 异步并发执行AI分类
     * 同时10个请求，所有数据都会执行完
     */
    @Override
    @Async
    public void Category() {
        // 查询所有待分类数据
        List<CategoryMessage> messageList = getCategoryMessage();
        if (messageList == null || messageList.isEmpty()) {
            log.info("暂无待分类数据");
            return;
        }

        log.info("开始并发分类，总数：{}，并发数：10", messageList.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 循环提交任务
        for (CategoryMessage message : messageList) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processMessage(message);
            }, EXECUTOR);
            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            log.info("========== 全部分类完成 ==========");
        } catch (Exception e) {
            log.error("批量任务异常", e);
        }
    }

    /**
     * 处理单条数据：调用API + 保存多条分类结果
     */
    private void processMessage(CategoryMessage message) {
        String projectId = String.valueOf(message.getId());
        try {

            // ===================== 非空校验：任意字段为null，直接跳过 =====================
            String overview = message.getSectionProjectOverview();
            String proname = message.getProname();
            String needs = message.getSectionProjectNeed();

            if (overview == null || proname == null || needs == null) {
                log.warn("项目ID[{}] 存在空字段，跳过API请求 ", projectId);
                return;
            }

            // ===================== 调用AI接口（返回 分类列表） =====================
            List<CategoryMessageDTO> dtoList = cozeApiClient.classifyByWorkflow(
                projectId,
                message.getSectionProjectOverview(),
                message.getProname(),
                message.getSectionProjectNeed()
            );

            // ===================== 批量保存到数据库 =====================
            if (dtoList != null && !dtoList.isEmpty()) {
                categoryUpdateService.insertAndUpdateCategoryInfo(dtoList);
                log.info("项目{} 保存成功，共{}条分类", projectId, dtoList.size());
            }

        } catch (IOException e) {
            log.error("项目{} 分类失败", projectId, e);
        }
    }

    @Override
    public List<CategoryMessage> getCategoryMessage() {
        return tenderProjectDetailParsedMapper.selectCategoryMessage();
    }
}
