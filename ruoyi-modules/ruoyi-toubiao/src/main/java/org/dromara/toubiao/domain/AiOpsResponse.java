package org.dromara.toubiao.domain;

import lombok.Data;

import java.util.List;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/4 20:53
 */

@Data
public class AiOpsResponse {

    private String status;
    private String summary;
    private String analysis;
    private List<SolutionStep> solution;
    private List<String> warnings;

    @Data
    public static class SolutionStep {
        private int step;
        private String action;
        private String command;
    }
}
