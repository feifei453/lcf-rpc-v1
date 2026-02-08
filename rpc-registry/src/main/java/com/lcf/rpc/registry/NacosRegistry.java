package com.lcf.rpc.registry;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NacosRegistry implements Registry {

    private final NamingService namingService;

    public NacosRegistry() {
        try {
            this.namingService = NamingFactory.createNamingService("192.168.200.130:8848");
        } catch (NacosException e) {
            throw new RuntimeException("连接 Nacos 失败", e);
        }
    }

    @Override
    public void register(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            namingService.registerInstance(serviceName, inetSocketAddress.getHostName(), inetSocketAddress.getPort());
        } catch (NacosException e) {
            throw new RuntimeException("Nacos 注册失败", e);
        }
    }

    // 修改后的 lookupAll 方法
    @Override
    public List<InetSocketAddress> lookupAll(String serviceName) {
        try {
            // 1. 获取所有健康的实例 (不再是 selectOne)
            List<Instance> instances = namingService.selectInstances(serviceName, true);

            if (instances == null || instances.isEmpty()) {
                throw new RuntimeException("服务未找到: " + serviceName);
            }

            // 2. 转换成 InetSocketAddress 列表
            List<InetSocketAddress> addressList = new ArrayList<>();
            for (Instance instance : instances) {
                addressList.add(new InetSocketAddress(instance.getIp(), instance.getPort()));
            }
            return addressList;

        } catch (NacosException e) {
            throw new RuntimeException("Nacos 服务发现失败", e);
        }
    }
}