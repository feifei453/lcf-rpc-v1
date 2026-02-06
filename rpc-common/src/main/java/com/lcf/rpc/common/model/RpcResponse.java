package com.lcf.rpc.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 响应传输对象
 * 对应：调用成功了吗？结果是什么？
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应对应的请求号
     * 作用：必须与 RpcRequest 中的 requestId 保持一致，客户端才能匹配。
     */
    private String requestId;

    /**
     * 响应状态码
     * 作用：200 表示成功，500 表示服务端报错。
     * 建议配合枚举使用。
     */
    private Integer code;

    /**
     * 响应信息 / 错误信息
     * 作用：如果失败，这里放报错信息（"Service not found", "Timeout"）。
     */
    private String message;

    /**
     * 响应数据 / 业务结果
     * 作用：目标方法执行后的返回值。如果是 void 方法，此处为 null。
     */
    private Object data;

    /**
     * 快捷方法：生成成功响应
     */
    public static RpcResponse success(Object data, String requestId) {
        return RpcResponse.builder()
                .code(200)
                .message("success")
                .requestId(requestId)
                .data(data)
                .build();
    }

    /**
     * 快捷方法：生成失败响应
     */
    public static RpcResponse fail(String message, String requestId) {
        return RpcResponse.builder()
                .code(500)
                .message(message)
                .requestId(requestId)
                .build();
    }
}