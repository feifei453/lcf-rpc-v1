package com.lcf.rpc.core.netty.codec;

import com.lcf.rpc.common.constant.RpcConstants;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.core.serialization.Serializer;
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
            byte[] bodyBytes = serializer.serialize(msg.getData());

            // 6. 写入数据长度 (4 bytes) - 关键！解决粘包
            out.writeInt(bodyBytes.length);

            // 7. 写入数据内容
            out.writeBytes(bodyBytes);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}