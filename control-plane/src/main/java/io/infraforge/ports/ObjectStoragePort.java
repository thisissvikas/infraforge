package io.infraforge.ports;

/**
 * Cloud-agnostic port for storing and retrieving binary/text objects (Terraform files,
 * plan outputs, etc.).
 *
 * <p>AWS implementation: S3 (see {@code adapters.aws.S3ObjectStorageAdapter}).
 * Local implementation: in-memory map (see {@code adapters.local.InMemoryObjectStorageAdapter}).</p>
 */
public interface ObjectStoragePort {

    /**
     * Store content at the given key within the named bucket/container.
     * Overwrites any existing object at the same key.
     */
    void put(String bucket, String key, byte[] content);

    /**
     * Retrieve the content of an object.
     *
     * @throws ObjectNotFoundException if the key does not exist in the bucket
     */
    byte[] get(String bucket, String key);

    /** Delete an object. No-op if the key does not exist. */
    void delete(String bucket, String key);

    class ObjectNotFoundException extends RuntimeException {
        public ObjectNotFoundException(String bucket, String key) {
            super("Object not found: " + bucket + "/" + key);
        }
    }
}
