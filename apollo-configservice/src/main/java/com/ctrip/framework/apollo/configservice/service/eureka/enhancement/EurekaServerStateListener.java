package com.ctrip.framework.apollo.configservice.service.eureka.enhancement;

import com.netflix.appinfo.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class EurekaServerStateListener {

    @Value("${notification.dingtalk.atMobiles}")
    private String atMobiles;

    @Value("${notification.dingtalk.token}")
    private String token;

    @Autowired
    private AsyncMessageHandler asyncMessageHandler;

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Logger logger = LoggerFactory.getLogger(EurekaServerStateListener.class);

    @EventListener
    public void listen(EurekaInstanceCanceledEvent eurekaInstanceCanceledEvent) {
        if(!eurekaInstanceCanceledEvent.isReplication()){
            asyncMessageHandler.updateStatus(eurekaInstanceCanceledEvent.getAppName(),eurekaInstanceCanceledEvent.getServerId(), InstanceInfo.InstanceStatus.DOWN);
            asyncMessageHandler.sendMessage("【提示】服务下线通知：服务名称 "+eurekaInstanceCanceledEvent.getAppName()+ " 实例 " + eurekaInstanceCanceledEvent.getServerId()+" , 时间：" + simpleDateFormat.format(new Date()));
        }else{
            logger.info("当前事件：{} 属于其他节点同步，不需要广播通知",eurekaInstanceCanceledEvent);
        }
    }

    @EventListener
    public void listen(EurekaInstanceRegisteredEvent event) {
        if(!event.isReplication()){
            logger.info("服务发生注册，开始通知其他服务");
            asyncMessageHandler.updateStatus(event.getInstanceInfo().getAppName(),event.getInstanceInfo().getInstanceId(),event.getInstanceInfo().getStatus());
            asyncMessageHandler.sendMessage("【提示】服务上线通知：服务名称 "+event.getInstanceInfo().getAppName()+ " 实例 " + event.getInstanceInfo().getInstanceId()+" , 时间：" + simpleDateFormat.format(new Date()));
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
