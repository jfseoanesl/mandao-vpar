package com.mandao.evidence;

import com.mandao.shared.MandaoProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

/** Almacenamiento S3 compatible (MinIO en el MVP, AWS S3 en producción sin cambios de código). */
@Service
@ConditionalOnProperty(prefix = "mandao.storage", name = "type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    public S3StorageService(MandaoProperties props) {
        var cfg = props.storage();
        this.client = MinioClient.builder()
                .endpoint(cfg.s3Endpoint())
                .credentials(cfg.s3AccessKey(), cfg.s3SecretKey())
                .build();
        this.bucket = cfg.s3Bucket();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar el bucket " + bucket, e);
        }
    }

    @Override
    public String put(byte[] data, String contentType, String prefix, String extension) {
        String key = prefix + "/" + UUID.randomUUID() + "." + extension;
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Error guardando archivo", e);
        }
    }

    @Override
    public byte[] get(String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo archivo", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Error eliminando archivo", e);
        }
    }
}
