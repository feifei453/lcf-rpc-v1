package com.lcf.rpc.core.transport;

import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.netty.handler.CommonDecoder;
import com.lcf.rpc.core.netty.handler.CommonEncoder;
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

    public void sendRequest(RpcRequest rpcRequest) {
        // 1. 创建线程组 (Client 一般只需要一个线程组)
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            // 2. 创建启动助手
            Bootstrap bootstrap = new Bootstrap();

            // 3. 配置参数
            bootstrap.group(group)
                    .channel(NioSocketChannel.class) // 指定使用 NIO
                    // 在 NettyClient.java 中找到这一段
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            Serializer serializer = new JdkSerializer();

                            // --- 新增: 解码器 (为了看懂服务端的回复) ---
                            // 客户端收到的是 RpcResponse
                            ch.pipeline().addLast(new CommonDecoder(serializer, RpcResponse.class));

                            // 原有的编码器
                            ch.pipeline().addLast(new CommonEncoder(serializer));

                            // 原有的业务 Handler
                            ch.pipeline().addLast(new NettyClientHandler());
                        }
                    });

            // 4. 连接服务端 (这里先写死本地，测试用)
            ChannelFuture future = bootstrap.connect("127.0.0.1", 8080).sync();
            log.info("客户端连接成功....");

            // 5. 发送数据
            // Netty 是异步的，writeAndFlush 会经过 Pipeline 里的 CommonEncoder
            Channel channel = future.channel();
            if (channel != null) {
                channel.writeAndFlush(rpcRequest).addListener(future1 -> {
                    if(future1.isSuccess()) {
                        log.info("请求发送成功: {}", rpcRequest.getRequestId());
                    } else {
                        log.error("请求发送失败:", future1.cause());
                    }
                });

                // 阻塞等待关闭，防止主线程立刻退出
                channel.closeFuture().sync();
            }

        } catch (InterruptedException e) {
            log.error("客户端异常", e);
        } finally {
            // 6. 优雅关闭线程组
            group.shutdownGracefully();
        }
    }
}