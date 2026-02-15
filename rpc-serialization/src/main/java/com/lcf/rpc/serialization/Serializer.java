package com.lcf.rpc.serialization;

/**
 * 序列化接口
 * 作用：负责将对象转换为字节数组，或将字节数组转换为对象
 */
public interface Serializer {

    /**
     * 序列化
     * @param object 要序列化的对象
     * @return 字节数组
     */
    byte[] serialize(Object object);

    /**
     * 反序列化
     * @param bytes 字节数组
     * @param clazz 目标类的 Class 对象
     * @return 反序列化后的对象
     * @param <T> 泛型
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);

    /**
     * 获取序列化算法的标识码
     * 作用：网络传输时，用来告诉服务端“我是用什么算法序列化的”
     * 例如：1-JDK, 2-JSON, 3-Hessian, 4-Kryo
     */
    byte getCode();
}