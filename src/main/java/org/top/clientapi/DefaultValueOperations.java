package org.top.clientapi;

import org.top.clientapi.codec.DefaultValueSerializer;
import org.top.clientapi.codec.ValueSerializer;
import org.top.core.machine.OptionEnum;
import org.top.exception.RaftException;

/**
 * @author lubeilin
 * @date 2020/12/16
 */
public class DefaultValueOperations<V> implements ValueOperations<V> {

    private ValueSerializer<V> valueSerializer;
    private CmdExecutor cmdExecutor = new CmdExecutor();
    private ValueSerializer<String> defaultSer = new DefaultValueSerializer<>();
    private Class<V> entityClass;

    protected DefaultValueOperations(ValueSerializer<V> valueSerializer, Class<V> entityClass) {
        this.valueSerializer = valueSerializer;
        this.entityClass = entityClass;
    }

    @Override
    public void delete(String key) {
        cmdExecutor.cmd(OptionEnum.DEL, defaultSer.serialize(key), null);
    }

    @Override
    public V get(String key) {
        byte[] data = cmdExecutor.cmd(OptionEnum.GET, defaultSer.serialize(key), null);
        if (data == null) {
            return null;
        }
        return valueSerializer.deserialize(data, entityClass);
    }

    @Override
    public void set(String key, V v) {
        cmdExecutor.cmd(OptionEnum.SET, defaultSer.serialize(key), valueSerializer.serialize(v));
    }

    @Override
    public long incr(String key) {
        return calculation(OptionEnum.INCR, key, null);
    }

    @Override
    public long incrBy(String key, long val) {
        return calculation(OptionEnum.INCR, key, val);
    }

    private long calculation(OptionEnum optionEnum, String key, Long val) {
        byte[] value = val == null ? null : defaultSer.serialize(Long.toString(val));
        byte[] data = cmdExecutor.cmd(optionEnum, defaultSer.serialize(key), value);
        try {
            String str = defaultSer.deserialize(data, String.class);
            return Long.parseLong(str);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RaftException("数据错误");
        }
    }

    @Override
    public long decr(String key) {
        return calculation(OptionEnum.DECR, key, null);
    }

    @Override
    public long decrBy(String key, long val) {
        return calculation(OptionEnum.DECR, key, val);
    }
}
