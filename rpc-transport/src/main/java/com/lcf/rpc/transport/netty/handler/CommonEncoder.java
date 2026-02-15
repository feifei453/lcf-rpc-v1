package com.lcf.rpc.transport.netty.handler;

import com.lcf.rpc.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

/**
 * 通用编码器
 * 作用：将 Java 对象序列化为字节数组，写入 Netty 的 ByteBuf
 */
@AllArgsConstructor
public class CommonEncoder extends MessageToByteEncoder<Object> {

    private final Serializer serializer;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        // 1. 使用序列化器将对象转为 byte[]
        byte[] bytes = serializer.serialize(msg);

        // 2. 将 byte[] 写入 Netty 的 ByteBuf
        out.writeBytes(bytes);
    }
}