package com.lcf.rpc.common.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SerializerCode {
    JSON((byte) 1),
    KRYO((byte) 2);

    private final byte code;
    public static byte getCodeByString(String name) {
        for (SerializerCode value : SerializerCode.values()) {
            // 忽略大小写比较 (KRYO == kryo)
            if (value.name().equalsIgnoreCase(name)) {
                return value.code;
            }
        }
        // 如果配置了不支持的序列化算法，直接抛错
        throw new IllegalArgumentException("不支持的序列化配置: " + name);
    }
}