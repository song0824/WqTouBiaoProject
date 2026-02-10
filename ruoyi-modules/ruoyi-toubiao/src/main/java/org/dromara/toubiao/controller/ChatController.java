package org.dromara.toubiao.controller;

/**
 * 功能：
 * 作者：张
 * 日期：2026/1/4 20:49
 */
import lombok.extern.slf4j.Slf4j;
import org.dromara.toubiao.domain.AiOpsResponse;
import org.dromara.toubiao.service.ConsultantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Slf4j
@RequestMapping("/api")
public class ChatController {


    @Autowired
    private ConsultantService consultantService;


    /**
     * 模型回答问题
     * 非流式
     * 多用户可同时使用
     * @param message
     * @return
     */
    @GetMapping("/chat")
    public ResponseEntity<AiOpsResponse> chat(@RequestParam String message) {
//        HttpServletRequest request
        //根据token获取用户ID

//        String token = request.getHeader("Authorization");

//        Long userId = JwtUtil.getUserIdFromToken(token);

        AiOpsResponse chat = consultantService.chat(String.valueOf(123), message);
        return ResponseEntity.ok(chat);
    }

    //TODO 模型对话----基于Qdrant向量数据库,123是userId

}
