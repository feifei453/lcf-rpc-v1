package com.lcf.rpc.common.model;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 请求传输对象
 * 对应：谁调用的？调用的哪个接口？哪个方法？参数是什么？
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RpcRequest implements Serializable {

    // 序列化版本号，防止序列化冲突
    private static final long serialVersionUID = 1L;

    /**
     * 请求号 / 链路ID
     * 重要作用：因为 Netty 是异步通信，响应回来时，客户端需要知道这个响应对应之前的哪一个请求。
     * 通常使用 UUID 或 AtomicLong 生成。
     */
    private String requestId;

    /**
     * 接口名称 (例如：com.lcf.rpc.UserService)
     * 作用：服务端收到后，去 Map<String, Object> 注册表中查找对应的 Service 实现类。
     */
    private String interfaceName;

    /**
     * 方法名称 (例如：getUserById)
     * 作用：反射调用时需要知道方法名。
     */
    private String methodName;

    /**
     * 参数类型列表 (例如：[java.lang.String, java.lang.Integer])
     * 作用：处理方法重载（Overload）。
     * 如果只有方法名和参数值，当存在同名方法（如 get(int) 和 get(String)）时，反射无法准确区分。
     */
    private Class<?>[] paramTypes;

    /**
     * 参数值列表 (例如：["1001", 18])
     * 作用：实际传递给方法的入参。
     */
    private Object[] parameters;

    // ----------- 进阶扩展字段 (V2.0 版本再考虑) -----------
    // private String version; // 服务版本号 (如 v1.0, v2.0)，用于灰度发布
    // private String group;   // 服务分组
}