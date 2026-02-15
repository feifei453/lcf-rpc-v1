package com.lcf.rpc.common.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class RpcProperties {

    private static final Properties properties = new Properties();

    // 静态代码块，类加载时自动读取
    static {
        try (InputStream inputStream = RpcProperties.class.getClassLoader().getResourceAsStream("rpc.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
                log.info("成功加载配置文件: rpc.properties");
            } else {
                log.warn("未找到 rpc.properties，将使用默认配置");
            }
        } catch (IOException e) {
            log.error("读取配置文件失败", e);
        }
    }

    // --- 获取配置的工具方法 (带默认值) ---

    public static String getRegistryAddress() {
        return properties.getProperty("rpc.registry.address", "127.0.0.1:2181");
    }

    public static String getRegistryType() {
        return properties.getProperty("rpc.registry.type", "zookeeper");
    }

    public static int getServerPort() {
        String port = properties.getProperty("rpc.server.port", "8080");
        return Integer.parseInt(port);
    }

    public static String getSerializer() {
        return properties.getProperty("rpc.serializer", "kryo");
    }

    public static String getLoadBalancer() {
        return properties.getProperty("rpc.loadbalancer", "consistentHash");
    }

    public static double getRateLimitQps() {
        String qps = properties.getProperty("rpc.ratelimit.qps", "100");
        return Double.parseDouble(qps);
    }
}