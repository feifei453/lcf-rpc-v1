package com.lcf.rpc.common.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RpcMessageType {
    REQUEST((byte) 1),
    RESPONSE((byte) 2),
    HEARTBEAT_REQUEST((byte) 3), // 客户端发 PING
    HEARTBEAT_RESPONSE((byte) 4); // 服务端回 PONG

    private final byte code;
}