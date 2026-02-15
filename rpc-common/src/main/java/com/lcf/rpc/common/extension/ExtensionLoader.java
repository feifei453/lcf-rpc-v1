package com.lcf.rpc.common.extension;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义 SPI 加载器 (优化版)
 * 核心优化：
 * 1. 优化 getExtension 中的 Holder 创建逻辑，利用 putIfAbsent 返回值减少查询。
 * 2. 规范 DCL 双重检查锁写法。
 */
@Slf4j
public class ExtensionLoader<T> {

    // 扩展配置文件的路径
    private static final String EXTENSION_DIR = "META-INF/extensions/";

    // 全局缓存：加载器实例 Map<接口, 加载器>
    private static final Map<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>();

    // 实例缓存：Map<扩展名, Holder<实例>>
    private final Map<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    // 类缓存：Map<扩展名, Class> (懒加载，只有在调用 getExtensionClasses 时才加载)
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    private final Class<T> type;

    private ExtensionLoader(Class<T> type) {
        this.type = type;
    }

    /**
     * 获取接口的加载器 (单例)
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type should not be null.");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type must be an interface.");
        }

        ExtensionLoader<T> loader = (ExtensionLoader<T>) LOADERS.get(type);
        if (loader == null) {
            LOADERS.putIfAbsent(type, new ExtensionLoader<>(type));
            loader = (ExtensionLoader<T>) LOADERS.get(type);
        }
        return loader;
    }

    /**
     * 获取扩展类实例 (核心方法)
     * @param name 扩展名 (例如 "json", "kryo")
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name should not be null or empty.");
        }

        // 1. 获取 Holder (存放单例的容器)
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            Holder<Object> newHolder = new Holder<>();
            // 利用 putIfAbsent 的原子性
            // 如果 key 不存在，放入 newHolder 并返回 null (表示成功)
            // 如果 key 已存在 (被其他线程抢先放入)，返回那个已存在的 holder
            Holder<Object> previous = cachedInstances.putIfAbsent(name, newHolder);
            holder = (previous != null) ? previous : newHolder;
        }

        // 2. 双重检查锁 (Double-Checked Locking) 创建单例
        Object instance = holder.get();
        // 第一次检查 (不加锁)
        if (instance == null) {
            synchronized (holder) {
                // 第二次检查 (加锁)
                instance = holder.get();
                if (instance == null) {
                    // 创建实例
                    instance = createExtension(name);
                    // 赋值给 volatile 变量
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 创建扩展实例 (加载类 -> 实例化)
     */
    private T createExtension(String name) {
        // 加载所有该接口下的扩展类
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new RuntimeException("No such extension of name " + name);
        }
        try {
            return (T) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Fail to create extension instance: " + name, e);
        }
    }

    /**
     * 获取所有扩展类 (懒加载 + DCL)
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = new ConcurrentHashMap<>();
                    loadDirectory(classes);
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * 读取配置文件
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses) {
        String fileName = EXTENSION_DIR + type.getName();
        try {
            Enumeration<URL> urls;
            ClassLoader classLoader = ExtensionLoader.class.getClassLoader();
            urls = classLoader.getResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    URL resourceUrl = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceUrl);
                }
            }
        } catch (IOException e) {
            log.error("Exception when load extension class file", e);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, URL resourceUrl) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int ci = line.indexOf('#');
                if (ci >= 0) {
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        final int ei = line.indexOf('=');
                        String name = line.substring(0, ei).trim();
                        String className = line.substring(ei + 1).trim();
                        if (name.length() > 0 && className.length() > 0) {
                            Class<?> clazz = classLoader.loadClass(className);
                            extensionClasses.put(name, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        log.error("Failed to load extension class", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Exception when load extension class file", e);
        }
    }
}

/**
 * 简单的容器，用于持有单例对象
 */
class Holder<T> {
    private volatile T value;
    public T get() { return value; }
    public void set(T value) { this.value = value; }
}