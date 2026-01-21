package org.dromara.toubiao.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.toubiao.client.HebeiHttpClientCommonFactory;
import org.dromara.toubiao.client.HebeiInfoUrlCommonClient;
import org.dromara.toubiao.domain.TenderProjectDetail;
import org.dromara.toubiao.mapper.GetMessageMapper;
import org.dromara.toubiao.service.HebeiTenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 河北招标信息服务实现类
 */
@Service
@Slf4j
public class HebeiTenderServiceImpl implements HebeiTenderService {

    @Autowired
    private GetMessageMapper getMessageMapper;

    @Autowired
    private HebeiInfoUrlCommonClient hebeiInfoUrlCommonClient;
    //防止多个同步任务同时运行
    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);

    /**
     *获取单个 infoUrl
     */
    @Override
    public String getInfoUrl(String infoId) {
        if (infoId == null || infoId.trim().isEmpty()) {
            log.warn("infoId 为空");
            return null;
        }

        try {
            return hebeiInfoUrlCommonClient.getFullInfoUrl(infoId);
        } catch (Exception e) {
            log.error("获取 infoId: {} 的 URL 失败", infoId, e);
            return null;
        }
    }

    /**
     * 批量同步: 将数据库中缺失的 infoUrl 补全
     */
    public void updateMissingInfoUrls() {
        if (!isSyncing.compareAndSet(false, true)) {
            log.warn("批量同步任务已在运行中，跳过本次请求");
            return;
        }

        try {
            // 1. 获取实体列表
            List<TenderProjectDetail> missingList = getMessageMapper.selectMissingInfoUrlList();
            if (missingList == null || missingList.isEmpty()) {
                log.info("未发现缺失 URL 的记录，同步结束");
                return;
            }

            log.info("开始批量同步，共 {} 条记录", missingList.size());

            // 2. 使用线程池并发处理实体对象
            List<CompletableFuture<Void>> futures = missingList.stream()
                .map(detail -> CompletableFuture.runAsync(() -> {
                    // 传入整个对象进行处理
                    processSingleUrl(detail);
                }, HebeiHttpClientCommonFactory.getExecutor()))
                .collect(Collectors.toList());

            // 3. 等待本批次所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("批量同步任务执行完毕");

        } catch (Exception e) {
            log.error("批量同步过程中发生异常", e);
        } finally {
            isSyncing.set(false);
        }
    }

    private void processSingleUrl(TenderProjectDetail detail) {
        // 获取业务主键 infoId
        String infoId = detail.getInfoid();
        if (infoId == null || infoId.isEmpty()) {
            return;
        }

        try {
            String fullUrl = hebeiInfoUrlCommonClient.getFullInfoUrl(infoId);

            if (fullUrl != null && !fullUrl.isEmpty()) {
                detail.setInfoUrl(fullUrl);
                getMessageMapper.updateTenderProjectDetail(detail);
                log.debug("成功更新 ID: {} 的 URL", infoId);
            } else {
                log.warn("未能获取到 ID: {} 的有效 URL", infoId);
            }

            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("处理记录 ID: {} 时发生异常", infoId, e);
        }
    }

}
