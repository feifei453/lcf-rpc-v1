package com.lcf.rpc.core.netty.handler;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.demo.provider.HelloServiceImpl;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端业务逻辑处理器
 */
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcMessage> { // 泛型改成 RpcMessage

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        RpcRequest request = (RpcRequest) msg.getData();
        log.info("服务端收到请求: {}", request);

        // --- ⚠️ 临时修改开始：手动模拟服务调用 ---
        Object result;
        if ("sayHello".equals(request.getMethodName())) {
            // 假装我们找到了实现类 (实际应该从 Map 里查)
            HelloServiceImpl service = new HelloServiceImpl();
            // 执行方法
            result = service.sayHello((String) request.getParameters()[0]);
        } else {
            result = "未找到方法";
        }
        // --- 临时修改结束 ---

        // 构造响应
        RpcResponse response = RpcResponse.success(result, request.getRequestId());

        // 发送回包... (同之前)
        RpcMessage responseMsg = RpcMessage.builder()
                .codec((byte) 1)
                .messageType(RpcMessageType.RESPONSE.getCode())
                .data(response)
                .build();
        ctx.writeAndFlush(responseMsg);
    }
}