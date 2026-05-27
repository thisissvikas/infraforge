package io.infraforge.adapters.local;

import io.infraforge.ports.ObjectStoragePort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory object store for local development and tests.
 * Keys are scoped as {@code bucket/key} internally.
 */
public class InMemoryObjectStorageAdapter implements ObjectStoragePort {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public void put(String bucket, String key, byte[] content) {
        store.put(compositeKey(bucket, key), content);
    }

    @Override
    public byte[] get(String bucket, String key) {
        byte[] val = store.get(compositeKey(bucket, key));
        if (val == null) {
            throw new ObjectNotFoundException(bucket, key);
        }
        return val;
    }

    @Override
    public void delete(String bucket, String key) {
        store.remove(compositeKey(bucket, key));
    }

    private static String compositeKey(String bucket, String key) {
        return bucket + "/" + key;
    }
}
