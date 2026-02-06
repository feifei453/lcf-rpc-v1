package com.lcf.rpc.common.constant;

public class RpcConstants {
    // 魔数：用来校验是不是我们的协议包 (比如 CAFEBABE，这里用 LCF-RPC 的 hex)
    public static final byte[] MAGIC_NUMBER = {(byte) 'l', (byte) 'c', (byte) 'f', (byte) 'r'};

    // 版本号
    public static final byte VERSION = 1;

    // 头部总长度 (魔数4 + 版本1 + 序列化1 + 类型1 + 长度4 = 11字节)
    public static final int HEAD_LENGTH = 11;
}