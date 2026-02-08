package com.lcf.rpc.core.filter;

import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import lombok.Data;

import java.util.Map;

@Data
public class FilterData {
    private String interfaceName;
    private String methodName;
    private Object[] args;
    private Map<String, Object> attachments; // 隐式传参
    private RpcResponse response; // 执行后的结果
    private long startTime; // 用于统计耗时

    // 构造函数：从 RpcRequest 转换而来
    public FilterData(RpcRequest request) {
        this.interfaceName = request.getInterfaceName();
        this.methodName = request.getMethodName();
        this.args = request.getParameters();
        this.attachments = request.getAttachments();
        this.startTime = System.currentTimeMillis();
    }
}