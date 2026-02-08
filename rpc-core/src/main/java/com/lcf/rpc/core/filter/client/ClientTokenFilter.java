package com.lcf.rpc.core.filter.client;

import com.lcf.rpc.core.filter.ClientBeforeFilter;
import com.lcf.rpc.core.filter.FilterData;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class ClientTokenFilter implements ClientBeforeFilter {
    @Override
    public void doFilter(FilterData filterData) {
        if (filterData.getAttachments() == null) {
            filterData.setAttachments(new HashMap<>());
        }
        // 模拟从上下文获取 Token
        filterData.getAttachments().put("token", "secret-token-123");
        log.info("客户端过滤器: 已添加 Token");
    }
}