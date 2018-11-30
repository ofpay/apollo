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
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

@Service
public class EurekaServerStateListener {

    @Value("${notification.dingtalk.atMobiles}")
    private String atMobiles;

    @Value("${notification.dingtalk.token}")
    private String token;


    private Logger logger = LoggerFactory.getLogger(EurekaServerStateListener.class);
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PeerAwareInstanceRegistry instanceRegistry;



    @EventListener
    public void listen(EurekaInstanceCanceledEvent eurekaInstanceCanceledEvent) {
        if(!eurekaInstanceCanceledEvent.isReplication()){
            updateStatus(eurekaInstanceCanceledEvent.getAppName(),eurekaInstanceCanceledEvent.getServerId(), InstanceInfo.InstanceStatus.DOWN);
            sendMessage("【提示】服务下线通知：服务名称 "+eurekaInstanceCanceledEvent.getAppName()+ " 实例 " + eurekaInstanceCanceledEvent.getServerId()+" , 时间：" + new Date() );
        }else{
            logger.info("当前事件：{} 属于其他节点同步，不需要广播通知",eurekaInstanceCanceledEvent);
        }
    }

    public void sendMessage(String message){
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("msgtype","text");
            JSONObject text = new JSONObject();
            text.put("content",message);
            jsonObject.put("text",text);
            jsonObject.put("atMobiles",atMobiles);
            restTemplate.postForEntity("https://oapi.dingtalk.com/robot/send?access_token="+token,jsonObject,String.class);
        }catch (Exception e){
            //NOOP
        }
    }


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

        Applications applications = instanceRegistry.getApplications();
        applications.getRegisteredApplications().forEach(application ->
                application.getInstances().forEach(instanceInfo -> {
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
                }));
    }

    @EventListener
    public void listen(EurekaInstanceRegisteredEvent event) {
        if(!event.isReplication()){
            logger.info("服务发生注册，开始通知其他服务");
            updateStatus(event.getInstanceInfo().getAppName(),event.getInstanceInfo().getInstanceId(),event.getInstanceInfo().getStatus());
            sendMessage("【提示】服务上线通知：服务名称 "+event.getInstanceInfo().getAppName()+ " 实例 " + event.getInstanceInfo().getInstanceId()+" , 时间：" + new Date() );
        }else{
            logger.info("当前事件：{} 属于其他节点同步，不需要广播通知",event);
        }
    }

    @EventListener
    public void listen(EurekaInstanceRenewedEvent event) {
        event.getAppName();
        event.getServerId();
    }

    @EventListener
    public void listen(EurekaRegistryAvailableEvent event) {

    }

    @EventListener
    public void listen(EurekaServerStartedEvent event) {
    }
}
