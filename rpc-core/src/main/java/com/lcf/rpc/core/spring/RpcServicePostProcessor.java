package com.lcf.rpc.core.spring;

import com.lcf.rpc.common.config.RpcProperties;
import com.lcf.rpc.common.extension.ExtensionLoader;
import com.lcf.rpc.core.annotation.RpcService;
import com.lcf.rpc.core.provider.ServiceProvider;
import com.lcf.rpc.core.provider.ServiceProviderImpl;
import com.lcf.rpc.core.transport.NettyServer;
import com.lcf.rpc.registry.Registry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Slf4j
@Component
public class RpcServicePostProcessor implements BeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {

    private final ServiceProvider serviceProvider;
    private final Registry registry;
    private final int serverPort;

    public RpcServicePostProcessor() {
        this.serviceProvider = new ServiceProviderImpl();
        // 读取配置
        String registryType = RpcProperties.getRegistryType();
        this.registry = ExtensionLoader.getExtensionLoader(Registry.class).getExtension(registryType);
        this.serverPort = RpcProperties.getServerPort();
    }

    /**
     * 1. 扫描 @RpcService，注册服务到本地 Map 和 注册中心
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 检查类上是否有 @RpcService 注解
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);

            // 获取服务接口 (优先取注解指定的，没指定就取第一个实现的接口)
            Class<?> interfaceClass = rpcService.interfaceClass();
            if (interfaceClass == void.class) {
                interfaceClass = bean.getClass().getInterfaces()[0];
            }
            String serviceName = interfaceClass.getName();

            // 1. 本地注册 (供 Netty 收到请求时查找实现类)
            serviceProvider.addServiceProvider(bean, serviceName);

            // 2. 远程注册 (发布到 ZK/Nacos)
            // 这里的 host 应该获取本机真实 IP，这里简化写死或读配置
            String host = "127.0.0.1";
            registry.register(serviceName, new InetSocketAddress(host, serverPort));

            log.info("Spring 发现服务: {} -> {}", serviceName, bean.getClass().getName());
        }
        return bean;
    }

    /**
     * 2. 容器启动完成后，启动 Netty 服务端
     * (避免每注册一个服务就启动一次，要在所有 Bean 加载完后统一启动)
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 只有父容器触发才启动 (防止 Spring MVC 子容器重复触发)
        if (event.getApplicationContext().getParent() == null) {
            new Thread(() -> {
                log.info("Spring 容器启动完成，正在开启 Netty 服务端...");
                new NettyServer(serverPort).start();
            }).start();
        }
    }
}