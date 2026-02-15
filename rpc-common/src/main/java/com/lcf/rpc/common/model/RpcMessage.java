package com.lcf.rpc.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RpcMessage {

    // --- 协议头 (Header) ---

    /**
     * 消息类型:
     * 1: RpcRequest
     * 2: RpcResponse
     */
    private byte messageType;

    /**
     * 序列化类型:
     * 1: JSON
     * 2: KRYO
     */
    private byte codec;

    // 注意：数据长度 (length) 不需要字段，编码时自动计算，解码时自动读取

    // --- 协议体 (Body) ---

    /**
     * 真正的业务数据 (RpcRequest 或 RpcResponse)
     */
    private Object data;
}