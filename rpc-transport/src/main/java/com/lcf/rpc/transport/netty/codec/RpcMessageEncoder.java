package com.lcf.rpc.transport.netty.codec;

import com.lcf.rpc.common.constant.RpcConstants;
import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {

    private final Serializer serializer;

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) throws Exception {
        try {
            // 1. 写入魔数 (4 bytes)
            out.writeBytes(RpcConstants.MAGIC_NUMBER);
            // 2. 写入版本号 (1 byte)
            out.writeByte(RpcConstants.VERSION);
            // 3. 写入序列化算法 (1 byte) - 这里暂时先写死，后续从 msg.getCodec() 获取
            out.writeByte(serializer.getCode());
            // 4. 写入消息类型 (1 byte)
            out.writeByte(msg.getMessageType());

            // 5. 序列化 Body
            byte[] bodyBytes = null;
            // 特殊处理心跳包：心跳包没有复杂的 body，不需要走序列化
            if (msg.getMessageType() == RpcMessageType.HEARTBEAT_REQUEST.getCode() ||
                    msg.getMessageType() == RpcMessageType.HEARTBEAT_RESPONSE.getCode()) {
                // 心跳数据直接转字节
                 bodyBytes = msg.getData().toString().getBytes();
            } else {
                // 普通业务数据，走序列化器
                 bodyBytes = serializer.serialize(msg.getData());
            }

            // 6. 写入数据长度 (4 bytes) - 关键！解决粘包
            out.writeInt(bodyBytes.length);

            // 7. 写入数据内容
            out.writeBytes(bodyBytes);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}