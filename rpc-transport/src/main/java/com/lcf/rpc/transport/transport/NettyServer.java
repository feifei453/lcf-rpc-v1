package com.lcf.rpc.transport.transport;

import com.lcf.rpc.common.config.RpcProperties;
import com.lcf.rpc.common.extension.ExtensionLoader;
import com.lcf.rpc.core.netty.codec.RpcMessageDecoder;
import com.lcf.rpc.core.netty.codec.RpcMessageEncoder;
import com.lcf.rpc.core.netty.handler.NettyServerHandler;
import com.lcf.rpc.core.serialization.Serializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyServer {

    private final int port;

    public NettyServer(int port) {
        this.port = port;
    }

    public void start() {
        // 1. 创建两个线程组
        // bossGroup 只负责接收连接，workerGroup 负责具体的读写业务
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            String serializerKey = RpcProperties.getSerializer();
                            Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(serializerKey);
                            log.info("服务端启动，使用序列化器: {}", serializerKey);
                            ch.pipeline().addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                            // Pipeline 就像工厂流水线，顺序非常重要！
                            // 📥 入站 (Byte -> Object): 解码器 -> Handler
                            // 📤 出站 (Object -> Byte): 编码器

                            // 替换原来的编解码器
                            ch.pipeline().addLast(new RpcMessageEncoder(serializer));
                            ch.pipeline().addLast(new RpcMessageDecoder(serializer));
                            ch.pipeline().addLast(new NettyServerHandler());
                        }
                    });

            // 4. 绑定端口，同步等待成功
            ChannelFuture future = serverBootstrap.bind(port).sync();
            log.info("RPC 服务端启动成功，监听端口: {}", port);

            // 5. 等待服务端监听端口关闭
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            log.error("服务端启动失败", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}