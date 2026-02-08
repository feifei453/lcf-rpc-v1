package com.lcf.rpc.core.netty.handler;

import com.lcf.rpc.common.enumeration.RpcMessageType;
import com.lcf.rpc.common.model.RpcMessage;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import com.lcf.rpc.core.filter.FilterConfig;
import com.lcf.rpc.core.filter.FilterData;
import com.lcf.rpc.core.provider.ServiceProviderImpl;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RpcMessage> {

    // 引入服务提供者 (这里先直接new，后续可以单例管理)
    private final ServiceProviderImpl serviceProvider = new ServiceProviderImpl();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        RpcRequest request = (RpcRequest) msg.getData();
        log.info("服务端收到请求: {}", request);

        RpcResponse response;
        try {
            // --- 🟢 插入点 3：执行 ServiceBefore 链 (鉴权) ---
            FilterData filterData = new FilterData(request);
            // 如果鉴权失败，这里会抛异常，直接跳到 catch 块，不会执行反射
            FilterConfig.getServiceBeforeChain().doFilter(filterData);
            // 1. 从本地注册表中获取服务实例
            String interfaceName = request.getInterfaceName();
            Object service = serviceProvider.getServiceProvider(interfaceName);

            // 2. 使用反射调用方法
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParamTypes());
            Object result = method.invoke(service, request.getParameters());

            // 3. 封装成功结果
            response = RpcResponse.success(result, request.getRequestId());
            // --- 🟢 插入点 4：执行 ServiceAfter 链 ---
            filterData.setResponse(response);
            FilterConfig.getServiceAfterChain().doFilter(filterData);

        } catch (Exception e) {
            // 捕获鉴权异常或业务异常
            String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("RPC执行失败: {}", errorMessage);
            response = RpcResponse.fail(errorMessage, request.getRequestId());
        }

        // 发送响应
        RpcMessage responseMsg = RpcMessage.builder()
                .codec((byte) 1)
                .messageType(RpcMessageType.RESPONSE.getCode())
                .data(response)
                .build();
        ctx.writeAndFlush(responseMsg);
    }
}