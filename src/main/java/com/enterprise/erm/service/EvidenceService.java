package com.enterprise.erm.service;

import com.enterprise.erm.domain.Evidence;
import com.enterprise.erm.domain.VerificationStatus;
import com.enterprise.erm.repository.EvidenceRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Evidence register storage. Files go through {@link FileStorageService}, so they share the one upload folder
 * (app.upload-dir), are identified by file signature (PDF / JPEG / PNG only) and are saved under a generated name.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceService {

    private final EvidenceRepository repo;
    private final FileStorageService storage;

    /** noRollbackFor: callers catch the "wrong file type" error and show it; that must not poison their transaction. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Evidence store(MultipartFile file, String type, String title, String recordedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Evidence file is required.");
        }
        Evidence.EvidenceType evidenceType;
        try {
            evidenceType = Evidence.EvidenceType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown evidence type: " + type);
        }

        String stored;
        try {
            stored = storage.saveDocument(file);   // throws IllegalArgumentException for a wrong file type
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store evidence file", ex);
        }

        Evidence e = new Evidence();
        e.setTitle(title);
        e.setType(evidenceType);
        e.setFileName(stored);
        e.setOriginalName(sanitizeName(file.getOriginalFilename()));
        e.setContentType(FileStorageService.mediaTypeFor(stored).toString());   // derived from our stored extension, not the client
        e.setSize(file.getSize());
        e.setRecordedOn(LocalDate.now());
        e.setRecordedBy(recordedBy);
        e.setVerificationStatus(VerificationStatus.PENDING);
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public void writeTo(Evidence e, HttpServletResponse response) throws IOException {
        if (e.getFileName() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "This evidence record has no file.");
            return;
        }
        Resource file = storage.load(e.getFileName());
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Evidence file missing on disk.");
            return;
        }
        response.setContentType(FileStorageService.mediaTypeFor(e.getFileName()).toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        String downloadName = e.getOriginalName() != null ? e.getOriginalName() : e.getFileName();
        response.setHeader("Content-Disposition", "attachment; filename=\"" + sanitizeName(downloadName) + "\"");
        response.setContentLengthLong(file.contentLength());
        try (InputStream in = file.getInputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    public List<Evidence> pending() {
        return repo.findByVerificationStatus(VerificationStatus.PENDING);
    }

    public List<Evidence> all() {
        return repo.findAll();
    }

    /** Only safe for evidence that nothing references; callers must check the FKs first. */
    @Transactional
    public void delete(Long id) {
        repo.findById(id).ifPresent(e -> {
            try {
                storage.delete(e.getFileName());
            } catch (IOException ex) {
                log.warn("Could not delete evidence file {}: {}", e.getFileName(), ex.getMessage());
            }
            repo.delete(e);
        });
    }

    /** Display / download name only: drops any path and anything that could break a header. */
    private static String sanitizeName(String original) {
        if (original == null || original.isBlank()) return "evidence";
        String base = original.replaceAll(".*[/\\\\]", "").replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
