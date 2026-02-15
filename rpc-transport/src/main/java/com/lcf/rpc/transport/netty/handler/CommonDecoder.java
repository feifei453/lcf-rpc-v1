package com.lcf.rpc.transport.netty.handler;

import com.lcf.rpc.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 通用解码器
 * 流程：ByteBuf -> byte[] -> Java Object
 */
@AllArgsConstructor
public class CommonDecoder extends ByteToMessageDecoder {

    private final Serializer serializer;
    private final Class<?> genericClass;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 简单的判空 (为了防止空包导致报错)
        if (in.readableBytes() < 4) {
            return;
        }


        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);

        // 2. 反序列化
        Object obj = serializer.deserialize(bytes, genericClass);
        out.add(obj);
    }
}