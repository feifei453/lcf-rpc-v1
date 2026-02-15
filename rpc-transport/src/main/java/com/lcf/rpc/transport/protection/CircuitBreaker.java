package com.lcf.rpc.transport.protection;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易版熔断器
 */
@Slf4j
public class CircuitBreaker {

    // 状态定义
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private volatile State state = State.CLOSED;

    // 统计指标
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    // 配置参数
    private final int failureThreshold = 2; // 失败次数阈值 (简化版用次数，标准版用百分比)
    private final long waitTime = 5000; // 熔断冷却时间 (5秒)
    private volatile long lastFailureTime = 0; // 上次进入熔断的时间

    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            // 如果在冷却时间内，直接拒绝
            if (System.currentTimeMillis() - lastFailureTime < waitTime) {
                return false;
            }
            // 冷却时间已过，进入半开状态，允许尝试一次
            log.info("熔断器冷却结束，进入半开状态 [HALF_OPEN]，尝试恢复...");
            state = State.HALF_OPEN;
            return true;
        }
        return true; // CLOSED 或 HALF_OPEN (其实 Half-Open 应该只允许一个，这里简化处理)
    }

    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            log.info("试探请求成功，熔断器关闭 [CLOSED]，系统恢复正常！");
            state = State.CLOSED;
            resetCounts();
        } else if (state == State.CLOSED) {
            resetCounts(); // 成功了就清空失败计数 (简化逻辑)
        }
    }

    public synchronized void recordFailure() {
        if (state == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                log.warn("失败次数达到阈值 ({})，熔断器打开 [OPEN]！暂停服务 {}ms", failures, waitTime);
                state = State.OPEN;
                lastFailureTime = System.currentTimeMillis();
            }
        } else if (state == State.HALF_OPEN) {
            log.warn("试探请求失败，熔断器继续保持打开 [OPEN]！");
            state = State.OPEN;
            lastFailureTime = System.currentTimeMillis();
        }
    }

    private void resetCounts() {
        failureCount.set(0);
        requestCount.set(0);
    }

    public State getState() {
        return state;
    }
}