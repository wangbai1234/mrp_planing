package com.mrp.importexport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path uploadDir;

    public FileStorageService(@Value("${mrp.file.upload-dir:./data/uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir);
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory: " + uploadDir, e);
        }
    }

    public StoredFile store(InputStream inputStream, String originalFileName) throws IOException {
        // Read all bytes first to avoid stream being consumed by checksum
        byte[] fileBytes = inputStream.readAllBytes();
        
        // Calculate checksum from bytes
        String checksum = computeChecksum(fileBytes);

        // Write bytes to file
        Path target = uploadDir.resolve(checksum + "_" + originalFileName);
        Files.copy(new ByteArrayInputStream(fileBytes), target, StandardCopyOption.REPLACE_EXISTING);

        log.info("Stored file: {} (size={}, checksum={})", target.getFileName(), fileBytes.length, checksum);
        return new StoredFile(target, checksum, originalFileName);
    }

    private String computeChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record StoredFile(Path path, String checksum, String originalFileName) {}
}
