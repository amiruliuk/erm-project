package com.enterprise.erm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Stores employee photos and credential documents on disk under generated names.
 * The file type is decided from the file's own first bytes, never from the client-supplied
 * Content-Type or filename, and the type served back is derived from the stored extension.
 */
@Service
public class FileStorageService {

    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> DOC_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:./data/uploads}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String save(MultipartFile file) throws IOException {
        return store(file, PHOTO_TYPES, "Only JPEG, PNG or WEBP images are allowed");
    }

    public String saveDocument(MultipartFile file) throws IOException {
        return store(file, DOC_TYPES, "Only PDF, JPEG or PNG documents are allowed");
    }

    public Resource load(String name) throws MalformedURLException {
        return new UrlResource(resolve(name).toUri());
    }

    public void delete(String name) throws IOException {
        if (name != null) Files.deleteIfExists(resolve(name));
    }

    /** Media type for a stored file, from its extension (set by us, so it can be trusted). */
    public static MediaType mediaTypeFor(String storedName) {
        String n = storedName == null ? "" : storedName.toLowerCase();
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (n.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    // ------------------------------------------------------------------ internals

    private String store(MultipartFile file, Set<String> allowed, String message) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException(message);
        String type = detectType(file);
        if (type == null || !allowed.contains(type)) throw new IllegalArgumentException(message);
        String ext = switch (type) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
        String name = UUID.randomUUID() + ext;   // never trust the original filename
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        return name;
    }

    private Path resolve(String name) {
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root)) throw new SecurityException("Invalid path");
        return p;
    }

    /** Identifies JPEG / PNG / WEBP / PDF by magic bytes. Returns null for anything else. */
    private static String detectType(MultipartFile f) throws IOException {
        byte[] h;
        try (InputStream in = f.getInputStream()) {
            h = in.readNBytes(12);
        }
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "image/png";
        if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') return "application/pdf";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "image/webp";
        return null;
    }
}
