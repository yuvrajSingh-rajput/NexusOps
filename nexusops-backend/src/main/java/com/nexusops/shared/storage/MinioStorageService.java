package com.nexusops.shared.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * MinIO S3-compatible storage service implementation.
 * Connects directly to local MinIO container on port 9000 using standard S3 REST endpoints.
 */
@Service
@ConditionalOnProperty(name = "nexusops.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final String endpoint;
    private final String bucketName;

    public MinioStorageService(
            @Value("${nexusops.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${nexusops.storage.minio.bucket-name:nexusops-documents}") String bucketName) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucketName = bucketName;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            URL url = URI.create(endpoint + "/" + bucketName).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            if (code == 404) {
                HttpURLConnection createConn = (HttpURLConnection) url.openConnection();
                createConn.setRequestMethod("PUT");
                createConn.setConnectTimeout(2000);
                createConn.setReadTimeout(2000);
                createConn.getResponseCode();
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("Could not verify/create MinIO bucket: {}. Will retry on store.", e.getMessage());
        }
    }

    @Override
    public void store(String storageKey, InputStream inputStream, long size, String contentType) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            URL url = URI.create(endpoint + "/" + bucketName + "/" + sanitizeKey(storageKey)).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", contentType != null ? contentType : "application/octet-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(bytes.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            if (code >= 300) {
                throw new RuntimeException("MinIO store failed with HTTP " + code);
            }
            log.debug("Successfully stored asset to MinIO: {}", storageKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object to MinIO: " + storageKey, e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        try {
            URL url = URI.create(endpoint + "/" + bucketName + "/" + sanitizeKey(storageKey)).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            if (code == 404) {
                throw new RuntimeException("Asset not found in MinIO: " + storageKey);
            }
            if (code >= 300) {
                throw new RuntimeException("MinIO load failed with HTTP " + code);
            }
            byte[] bytes = conn.getInputStream().readAllBytes();
            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve object from MinIO: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            URL url = URI.create(endpoint + "/" + bucketName + "/" + sanitizeKey(storageKey)).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(3000);
            conn.getResponseCode();
        } catch (Exception e) {
            log.warn("Failed to delete asset from MinIO: {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            URL url = URI.create(endpoint + "/" + bucketName + "/" + sanitizeKey(storageKey)).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String sanitizeKey(String key) {
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
