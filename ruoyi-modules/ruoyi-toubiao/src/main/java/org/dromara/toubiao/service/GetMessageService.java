package org.dromara.toubiao.service;

import org.dromara.toubiao.domain.TenderProjectDetail;
import org.dromara.toubiao.mapper.GetMessageMapper;
import org.dromara.toubiao.utils.PaChong.GetMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 功能：
 * 作者：张
 * 日期：2025/12/24 18:41
 */
@Service
public class GetMessageService {

    @Autowired
    private GetMessage getMessage;

    @Autowired
    private GetMessageMapper getMessageMapper;


    @Transactional
     public String WriteToDataBase() {


         List<TenderProjectDetail> tenderProjectDetails = getMessage.getTenderProjectList();

         System.out.println(tenderProjectDetails);

         int count = getMessageMapper.insertIntoTenderProjectList(tenderProjectDetails);
         if(count>0){
             // 打印获取到的项目详情
//             System.out.println(tenderProjectDetails);
             return "ok";
         }
         return "false";

    }

}
