package com.lcf.rpc.transport.provider;

/**
 * 本地服务提供者注册表接口
 * 用于保存服务端本地的 [接口名 -> 实现类实例] 的映射关系
 */
public interface ServiceProvider {

    /**
     * 保存服务实例
     * @param service      服务实例对象 (Spring Bean)
     * @param serviceName  服务名称 (通常是接口全限定名)
     */
    void addServiceProvider(Object service, String serviceName);

    /**
     * 获取服务实例
     * @param serviceName 服务名称
     * @return 服务实例对象
     */
    Object getServiceProvider(String serviceName);
}