package com.lcf.rpc.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.lcf.rpc.common.model.RpcRequest;
import com.lcf.rpc.common.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
public class KryoSerializer implements Serializer {

    /**
     * Kryo 线程不安全，必须使用 ThreadLocal
     */
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 支持循环引用
        kryo.setReferences(true);
        // 关闭注册行为 (允许序列化未注册的类，方便但稍微牺牲一点点性能)
        kryo.setRegistrationRequired(false);
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {
            Kryo kryo = kryoThreadLocal.get();
            kryo.writeObject(output, obj);
            // 必须 flush 否则数据可能写不进去
            output.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("Kryo 序列化失败", e);
            throw new RuntimeException("Kryo serialize failed");
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             Input input = new Input(byteArrayInputStream)) {
            Kryo kryo = kryoThreadLocal.get();
            return kryo.readObject(input, clazz);
        } catch (Exception e) {
            log.error("Kryo 反序列化失败", e);
            throw new RuntimeException("Kryo deserialize failed");
        }
    }

    @Override
    public byte getCode() {
        return 2;
    }
}