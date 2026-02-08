package com.lcf.rpc.core.filter.server;

import com.lcf.rpc.core.filter.FilterData;
import com.lcf.rpc.core.filter.ServiceBeforeFilter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ServiceTokenFilter implements ServiceBeforeFilter {
    @Override
    public void doFilter(FilterData filterData) {
        Map<String, Object> attachments = filterData.getAttachments();
        String token = (attachments != null) ? (String) attachments.get("token") : null;

        if (!"secret-token-123".equals(token)) {
            throw new RuntimeException("非法访问: Token 无效或缺失");
        }
        log.info("服务端过滤器: Token 校验通过");
    }
}