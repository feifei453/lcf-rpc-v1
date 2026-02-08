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
 * 自定义 SPI 加载器 (简化版 Dubbo SPI)
 */
@Slf4j
public class ExtensionLoader<T> {

    // 扩展配置文件的路径，例如 META-INF/extensions/com.lcf.rpc.core.serialization.Serializer
    private static final String EXTENSION_DIR = "META-INF/extensions/";

    // 缓存加载器实例：Map<接口, 加载器>
    private static final Map<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>();

    // 缓存扩展类实例：Map<扩展名, 实例>
    private final Map<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    // 缓存扩展类字节码：Map<扩展名, Class>
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    private final Class<T> type;

    private ExtensionLoader(Class<T> type) {
        this.type = type;
    }

    /**
     * 获取某个接口的加载器
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
     * 获取扩展类实例 (单例)
     * @param name 扩展名 (例如 "json", "jdk")
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name should not be null or empty.");
        }

        // 1. 获取或创建 Holder
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }

        // 2. 双重检查锁创建单例
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    private T createExtension(String name) {
        // 1. 加载所有扩展类 (读取配置文件)
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new RuntimeException("No such extension of name " + name);
        }
        try {
            // 2. 反射实例化
            return (T) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Fail to create extension instance: " + name, e);
        }
    }

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
                // 忽略注释 #
                final int ci = line.indexOf('#');
                if (ci >= 0) {
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.length() > 0) {
                    try {
                        // 格式: name=fullClassName
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
 * 简单的容器，用于持有对象 (类似于 ThreadLocal 的用法，但这里是普通对象)
 */
class Holder<T> {
    private volatile T value;
    public T get() { return value; }
    public void set(T value) { this.value = value; }
}