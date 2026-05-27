package io.infraforge.adapters.aws;

import io.infraforge.ports.ObjectStoragePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@Profile({"aws", "local"})
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3;

    public S3ObjectStorageAdapter(S3Client s3) {
        this.s3 = s3;
    }

    @Override
    public void put(String bucket, String key, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String bucket, String key) {
        try {
            return s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toBytes()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ObjectNotFoundException(bucket, key);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
