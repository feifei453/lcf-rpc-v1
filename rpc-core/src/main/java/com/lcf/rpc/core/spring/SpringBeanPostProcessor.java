package com.lcf.rpc.core.spring;

import com.lcf.rpc.core.annotation.RpcReference;
import com.lcf.rpc.core.annotation.RpcService;
import com.lcf.rpc.core.provider.ServiceProviderImpl;
import com.lcf.rpc.core.proxy.RpcClientProxy;
import com.lcf.rpc.core.transport.NettyClient;
import com.lcf.rpc.registry.NacosRegistry;
import com.lcf.rpc.registry.Registry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {

    private final ServiceProviderImpl serviceProvider;
    private final Registry registry;
    private final RpcClientProxy rpcClientProxy;

    public SpringBeanPostProcessor() {
        this.serviceProvider = new ServiceProviderImpl();
        this.registry = new NacosRegistry();
        this.rpcClientProxy = new RpcClientProxy(new NettyClient());
    }

    /**
     * 在 Bean 初始化之前执行 (这里我们用不到，直接返回 bean)
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * ⚠️ 核心逻辑：在 Bean 初始化之后执行
     */
    @Override
    @SneakyThrows
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();

        // --- 逻辑 1：处理服务端 @RpcService ---
        if (targetClass.isAnnotationPresent(RpcService.class)) {
            RpcService rpcService = targetClass.getAnnotation(RpcService.class);

            // 1. 获取服务接口名
            // 如果注解里没写 interfaceClass，就默认取第一个接口
            Class<?> interfaceClass;
            if (rpcService.interfaceClass() == void.class) {
                interfaceClass = targetClass.getInterfaces()[0];
            } else {
                interfaceClass = rpcService.interfaceClass();
            }
            String serviceName = interfaceClass.getCanonicalName();

            // 2. 注册到本地缓存 (为了反射调用)
            serviceProvider.addServiceProvider(bean);

            // 3. 注册到 Nacos
            // ⚠️ 这里为了演示简单，硬编码端口 8080，实际生产中应读取配置文件
            String host = InetAddress.getLocalHost().getHostAddress();
            int port = 8080;
            registry.register(serviceName, new InetSocketAddress(host, port));

            log.info("Spring 发现服务，已自动注册: {}", serviceName);
        }

        // --- 逻辑 2：处理客户端 @RpcReference ---
        // 遍历所有字段，看谁头上顶着 @RpcReference
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field field : declaredFields) {
            if (field.isAnnotationPresent(RpcReference.class)) {
                // 1. 生成代理对象
                Class<?> interfaceClass = field.getType();
                Object proxy = rpcClientProxy.getProxy(interfaceClass);

                // 2. 暴力反射注入
                field.setAccessible(true);
                field.set(bean, proxy);

                log.info("Spring 发现引用，已自动注入代理: {} -> {}", beanName, field.getName());
            }
        }

        return bean;
    }
}