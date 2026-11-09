package com.nexusops.shared.storage;

import java.io.InputStream;

/**
 * Storage abstraction for persisting and retrieving binary assets (documents, attachments).
 * Decouples domain logic from whether binaries live in local filesystem, MinIO, or Cloud S3.
 */
public interface StorageService {

    /**
     * Store binary data under the specified unique key.
     *
     * @param storageKey  Relative storage key (e.g. {orgId}/knowledge/{docId}/{version}/{filename})
     * @param inputStream Content input stream
     * @param size        Content size in bytes
     * @param contentType MIME content type
     */
    void store(String storageKey, InputStream inputStream, long size, String contentType);

    /**
     * Load binary stream by storage key.
     *
     * @param storageKey Relative storage key
     * @return Content input stream
     */
    InputStream load(String storageKey);

    /**
     * Delete stored asset by key.
     *
     * @param storageKey Relative storage key
     */
    void delete(String storageKey);

    /**
     * Check if asset exists.
     *
     * @param storageKey Relative storage key
     * @return true if exists
     */
    boolean exists(String storageKey);
}

