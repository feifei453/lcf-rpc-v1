package com.lcf.rpc.transport.filter;

import java.util.ArrayList;
import java.util.List;

public class FilterChain {
    private List<Filter> filters = new ArrayList<>();

    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    public void doFilter(FilterData data) {
        for (Filter filter : filters) {
            filter.doFilter(data);
        }
    }
}