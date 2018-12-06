package com.ctrip.framework.apollo.configservice.service.eureka.enhancement;

import com.alibaba.fastjson.JSONObject;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

@Service
public class AsyncMessageHandler {

    private Logger logger = LoggerFactory.getLogger(AsyncMessageHandler.class);

    @Value("${notification.dingtalk.atMobiles}")
    private String atMobiles;

    @Value("${notification.dingtalk.token}")
    private String token;


    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PeerAwareInstanceRegistry instanceRegistry;

    @Async
    public void updateStatus(String appName, String instanceId, InstanceInfo.InstanceStatus status) {
        logger.info("开始更新服务状态：{}，{}，{}", appName, instanceId, status.name());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType( MediaType.APPLICATION_JSON_UTF8);
        headers.add("Accept", APPLICATION_JSON_UTF8_VALUE);
        InstanceInfo eventInstanceInfo = instanceRegistry.getInstanceByAppAndId(appName, instanceId);
        if (eventInstanceInfo == null) {
            return;
        }
        if (status.compareTo(InstanceInfo.InstanceStatus.DOWN) == 0) {
            logger.info("服务：{}，{}已经断线！", appName, instanceId);
        }
        //更新实例的状态和最后更新时间戳
        InstanceInfo eventualInstanceInfo = eventInstanceInfo;
        eventualInstanceInfo.setLastUpdatedTimestamp();
        eventualInstanceInfo.setStatus(status);


        //找到当前更新状态的服务消费里列表，只有满足消费关系的机器才会被通知
        Applications applications = instanceRegistry.getApplications();


        List<InstanceInfo> filtered = applications.getRegisteredApplications().stream().map(application -> application.getInstances().stream().filter(instanceInfo -> Arrays.asList(instanceInfo.getMetadata().getOrDefault("consumes", "").split(",")).stream().map(String::toLowerCase).collect(Collectors.toList()).contains(eventualInstanceInfo.getAppName().toLowerCase())).collect(Collectors.toList())).flatMap(item->item.stream()).collect(Collectors.toList());

        logger.info("服务{} 配置的消费服务列表是：{},将依次通知各个对应的服务器",eventualInstanceInfo.getInstanceId(),filtered.stream().map(InstanceInfo::getInstanceId).collect(Collectors.toList()));

        filtered.forEach(instanceInfo -> {
                    String listenPath = instanceInfo.getMetadata().get("listen-url");
                    if (!instanceInfo.getInstanceId().equals(instanceId)) {
                        logger.info("开始通知通知服务{} 更新消费服务{} 的状态 {}", instanceInfo.getInstanceId(), instanceId, status.name());
                        if (StringUtils.isNotEmpty(listenPath)) {
                            String listenUrl = "http://" + instanceInfo.getIPAddr() + ":" + instanceInfo.getPort() + listenPath;
                            try {
                                HttpEntity<InstanceInfo> formEntity = new HttpEntity<>(eventualInstanceInfo, headers);
                                ResponseEntity<String> result = restTemplate.postForEntity(listenUrl, formEntity, String.class);
                                logger.info("服务通知结果：", result);
                            } catch (Exception e) {
                                logger.info("通知异常,忽略");
                            }
                        }
                    }
         });
    }

    @Async
    public void sendMessage(String message){
        try{
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType( MediaType.APPLICATION_JSON_UTF8);
            headers.add("Accept", APPLICATION_JSON_UTF8_VALUE);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgtype","text");
            JSONObject text = new JSONObject();
            text.put("content",message);
            jsonObject.put("text",text);
            jsonObject.put("atMobiles",atMobiles);
            HttpEntity<String> formEntity = new HttpEntity<>(jsonObject.toJSONString(), headers);
            ResponseEntity<String> result = restTemplate.postForEntity("https://oapi.dingtalk.com/robot/send?access_token="+token,formEntity, String.class);
            logger.info("发动钉钉消息接口返回：{}",result.getBody());
        }catch (Exception e){
            logger.info("消息通知发生异常",e);
        }
    }

}
