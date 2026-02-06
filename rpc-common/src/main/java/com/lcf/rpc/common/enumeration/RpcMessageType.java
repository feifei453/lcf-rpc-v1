package com.lcf.rpc.common.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RpcMessageType {
    REQUEST((byte) 1),
    RESPONSE((byte) 2);

    private final byte code;
}