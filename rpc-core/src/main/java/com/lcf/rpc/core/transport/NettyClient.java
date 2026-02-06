package com.lcf.rpc.core.transport;

import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.core.netty.codec.RpcMessageDecoder;
import com.lcf.rpc.core.netty.codec.RpcMessageEncoder;
import com.lcf.rpc.core.netty.handler.NettyClientHandler;
import com.lcf.rpc.core.serialization.JdkSerializer;
import com.lcf.rpc.core.serialization.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyClient {


    public void sendRequest(RpcMessage rpcMessage) {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true) // 开启 TCP Keepalive
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 这里先临时写死用 JDK 序列化，后续会优化
                            Serializer serializer = new JdkSerializer();

                            // ⚠️ 修改点2：使用自定义协议的编解码器
                            // 编码器：RpcMessage -> ByteBuf
                            pipeline.addLast(new RpcMessageEncoder(serializer));

                            // 解码器：ByteBuf -> RpcMessage
                            // 注意：Decoder 里已经处理了粘包和半包逻辑
                            pipeline.addLast(new RpcMessageDecoder(serializer));

                            // 业务处理器
                            pipeline.addLast(new NettyClientHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect("127.0.0.1", 8080).sync();
            log.info("客户端连接成功....");

            Channel channel = future.channel();
            if (channel != null) {
                // ⚠️ 修改点3：发送协议包
                channel.writeAndFlush(rpcMessage).addListener(future1 -> {
                    if(future1.isSuccess()) {
                        // 这里打印一下日志，方便调试
                        log.info("协议包发送成功，消息类型: {}", rpcMessage.getMessageType());
                    } else {
                        log.error("发送失败:", future1.cause());
                    }
                });

                channel.closeFuture().sync();
            }

        } catch (InterruptedException e) {
            log.error("客户端异常", e);
        } finally {
            group.shutdownGracefully();
        }
    }
}