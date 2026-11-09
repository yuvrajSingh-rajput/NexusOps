package com.nexusops.shared.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem-backed StorageService.
 * Used for local development and offline environments.
 */
@Service
@ConditionalOnProperty(name = "nexusops.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private final Path rootPath;

    public LocalStorageService(@Value("${nexusops.storage.local-dir:./data/storage}") String storageDir) {
        this.rootPath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
            log.info("Initialized LocalStorageService at {}", this.rootPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize local storage root directory: " + rootPath, e);
        }
    }

    @Override
    public void store(String storageKey, InputStream inputStream, long size, String contentType) {
        Path targetPath = resolveAndValidate(storageKey);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored asset at {}", targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write asset to local storage: " + storageKey, e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        Path targetPath = resolveAndValidate(storageKey);
        if (!Files.exists(targetPath)) {
            throw new RuntimeException("Asset not found in local storage: " + storageKey);
        }
        try {
            return new BufferedInputStream(new FileInputStream(targetPath.toFile()));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Asset not found in local storage: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path targetPath = resolveAndValidate(storageKey);
        try {
            Files.deleteIfExists(targetPath);
            log.debug("Deleted asset at {}", targetPath);
        } catch (IOException e) {
            log.warn("Failed to delete asset at {}: {}", targetPath, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        Path targetPath = resolveAndValidate(storageKey);
        return Files.exists(targetPath);
    }

    private Path resolveAndValidate(String storageKey) {
        String sanitizedKey = storageKey.replace("\\", "/");
        if (sanitizedKey.startsWith("/")) {
            sanitizedKey = sanitizedKey.substring(1);
        }
        Path resolved = rootPath.resolve(sanitizedKey).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new SecurityException("Potential path traversal detected in storage key: " + storageKey);
        }
        return resolved;
    }
}

