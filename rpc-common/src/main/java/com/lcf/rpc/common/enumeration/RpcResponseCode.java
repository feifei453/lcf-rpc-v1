package com.lcf.rpc.common.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RpcResponseCode {

    SUCCESS(200, "调用成功"),
    FAIL(500, "调用失败"),
    NOT_FOUND_METHOD(501, "未找到指定方法"),
    NOT_FOUND_CLASS(502, "未找到指定类");

    private final int code;
    private final String message;
}