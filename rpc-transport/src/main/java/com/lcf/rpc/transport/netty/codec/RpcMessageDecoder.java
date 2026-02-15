package com.lcf.rpc.transport.netty.codec;

import com.lcf.rpc.common.constant.RpcConstants;
import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.AllArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
public class RpcMessageDecoder extends ByteToMessageDecoder {

    private final Serializer serializer;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 检查是否能读取头部 (最少 11 字节)
        if (in.readableBytes() < RpcConstants.HEAD_LENGTH) {
            return; // 字节不够，不读，等待下一次
        }

        // 2. 标记读索引 (如果后续检查失败，可以回滚到这里)
        in.markReaderIndex();

        // 3. 检查魔数 (4 bytes)
        byte[] magic = new byte[4];
        in.readBytes(magic);
        if (!Arrays.equals(magic, RpcConstants.MAGIC_NUMBER)) {
            throw new IllegalArgumentException("Unknown magic code: " + Arrays.toString(magic));
        }

        // 4. 读取头部其他信息
        byte version = in.readByte();
        byte serializerCode = in.readByte();
        byte messageType = in.readByte();
        int bodyLength = in.readInt(); // 数据长度

        // 5. 检查数据包是否完整
        if (in.readableBytes() < bodyLength) {
            // 关键：如果不完整，重置读索引，等待下一次数据到齐
            in.resetReaderIndex();
            return;
        }

        // 6. 读取 Body
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);

        // 7. 反序列化 (⚠️ 核心修改点)
        Object body;

        // 情况 A: 心跳包 (PING/PONG) -> 直接转 String，不走 序列化器
        if (messageType == RpcMessageType.HEARTBEAT_REQUEST.getCode() ||
                messageType == RpcMessageType.HEARTBEAT_RESPONSE.getCode()) {
            body = new String(bodyBytes, StandardCharsets.UTF_8);
        }
        // 情况 B: 普通业务请求 -> 转 RpcRequest
        else if (messageType == RpcMessageType.REQUEST.getCode()) {
            body = serializer.deserialize(bodyBytes, RpcRequest.class);
        }
        // 情况 C: 普通业务响应 -> 转 RpcResponse
        else if (messageType == RpcMessageType.RESPONSE.getCode()) {
            body = serializer.deserialize(bodyBytes, RpcResponse.class);
        }
        // 情况 D: 未知类型
        else {
            throw new IllegalArgumentException("Unknown message type: " + messageType);
        }

        // 8. 封装成 RpcMessage 传递给下一个 Handler
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec(serializerCode)
                .messageType(messageType)
                .data(body)
                .build();

        out.add(rpcMessage);
    }
}