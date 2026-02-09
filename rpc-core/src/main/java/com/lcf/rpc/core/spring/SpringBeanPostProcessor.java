package com.lcf.rpc.core.spring;

import com.lcf.rpc.common.extension.ExtensionLoader;
import com.lcf.rpc.core.annotation.RpcReference;
import com.lcf.rpc.core.annotation.RpcService;
import com.lcf.rpc.core.provider.ServiceProviderImpl;
import com.lcf.rpc.core.proxy.RpcClientProxy;
import com.lcf.rpc.core.transport.NettyClient;
import com.lcf.rpc.registry.Registry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {

    private final ServiceProviderImpl serviceProvider;
    private final Registry registry;
    private final RpcClientProxy rpcClientProxy;

    public SpringBeanPostProcessor() {
        System.out.println("========== [Trace] SpringBeanPostProcessor 构造函数开始执行 ==========");
        this.serviceProvider = new ServiceProviderImpl();

        try {
            // 追踪点 1：检查 ExtensionLoader 加载了什么鬼东西
            Registry zk = ExtensionLoader.getExtensionLoader(Registry.class).getExtension("zookeeper");
            System.out.println("========== [Trace] SPI 加载结果: " + zk.getClass().getCanonicalName() + " ==========");
            this.registry = zk;
        } catch (Exception e) {
            System.err.println("========== [Trace] SPI 加载失败! ==========");
            e.printStackTrace();
            throw e; // 抛出异常，炸掉启动流程
        }

        this.rpcClientProxy = new RpcClientProxy(new NettyClient());
        System.out.println("========== [Trace] SpringBeanPostProcessor 构造函数执行完毕 ==========");
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    @SneakyThrows
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();

        if (targetClass.isAnnotationPresent(RpcService.class)) {
            System.out.println("========== [Trace] 发现服务类: " + beanName + " ==========");
            RpcService rpcService = targetClass.getAnnotation(RpcService.class);

            Class<?> interfaceClass;
            if (rpcService.interfaceClass() == void.class) {
                interfaceClass = targetClass.getInterfaces()[0];
            } else {
                interfaceClass = rpcService.interfaceClass();
            }
            String serviceName = interfaceClass.getCanonicalName();

            serviceProvider.addServiceProvider(bean);
            System.out.println("========== [Trace] 已添加本地缓存: " + serviceName + " ==========");

            // 追踪点 2：准备注册到 Zookeeper
            try {
                // ⚠️ 这里为了演示简单，硬编码端口 8080，实际生产中应读取配置文件
                // 请确保这个端口和你 NettyServer 启动的端口一致！！！
                registry.register(serviceName, new InetSocketAddress("127.0.0.1", 8080));
                System.out.println("========== [Trace] Zookeeper 注册方法调用完成: " + serviceName + " ==========");
            } catch (Exception e) {
                System.err.println("========== [Trace] Zookeeper 注册炸了! ==========");
                e.printStackTrace();
            }
        }

        // ... 客户端注入逻辑不变 ...
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field field : declaredFields) {
            if (field.isAnnotationPresent(RpcReference.class)) {
                Class<?> interfaceClass = field.getType();
                Object proxy = rpcClientProxy.getProxy(interfaceClass);
                field.setAccessible(true);
                field.set(bean, proxy);
                System.out.println("========== [Trace] 注入代理对象: " + field.getName() + " ==========");
            }
        }

        return bean;
    }
}