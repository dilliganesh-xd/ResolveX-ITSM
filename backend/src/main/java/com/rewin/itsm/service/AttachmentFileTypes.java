package com.rewin.itsm.service;

import com.rewin.itsm.common.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The file types an incident may carry, and the checks that a file really is
 * the type its name claims.
 *
 * <p>The type is decided here, from the extension, and confirmed against the
 * file's first bytes. The {@code Content-Type} the browser sends is ignored: it
 * is whatever the client says it is.
 */
final class AttachmentFileTypes {

    /** Extension to the content type it is stored and served with. */
    private static final Map<String, String> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("png", "image/png");
        ALLOWED.put("jpg", "image/jpeg");
        ALLOWED.put("jpeg", "image/jpeg");
        ALLOWED.put("gif", "image/gif");
        ALLOWED.put("webp", "image/webp");
        ALLOWED.put("pdf", "application/pdf");
        ALLOWED.put("txt", "text/plain");
        ALLOWED.put("log", "text/plain");
        ALLOWED.put("csv", "text/csv");
        ALLOWED.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ALLOWED.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /** How many leading bytes {@link #requireContentMatches} needs. */
    static final int SNIFF_BYTES = 8192;

    private static final int MAX_NAME_LENGTH = 255;

    private AttachmentFileTypes() {
    }

    /** A validated upload: the name to show, its extension and its content type. */
    record FileType(String fileName, String extension, String contentType) {
    }

    /** Cleans the uploaded name and rejects any extension not on the list. */
    static FileType fromFileName(String originalName) {
        String fileName = cleanName(originalName);
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = ALLOWED.get(extension);
        if (contentType == null) {
            throw new BadRequestException("Only " + describeAllowed() + " files can be attached.");
        }
        return new FileType(fileName, extension, contentType);
    }

    /**
     * Rejects a file whose bytes do not match its extension - an executable
     * renamed to {@code .png}, for example.
     *
     * @param head the first bytes of the file, up to {@link #SNIFF_BYTES}
     */
    static void requireContentMatches(FileType type, byte[] head) {
        boolean matches = switch (type.extension()) {
            case "png" -> startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWithText(head, 0, "GIF87a") || startsWithText(head, 0, "GIF89a");
            case "webp" -> startsWithText(head, 0, "RIFF") && startsWithText(head, 8, "WEBP");
            case "pdf" -> startsWithText(head, 0, "%PDF-");
            // Office Open XML documents are ZIP containers.
            case "docx", "xlsx" -> startsWith(head, 'P', 'K', 0x03, 0x04);
            // Text has no signature; a NUL byte is the reliable sign of a binary file.
            case "txt", "log", "csv" -> !containsNul(head);
            default -> false;
        };
        if (!matches) {
            throw new BadRequestException("The file's contents do not match its ." + type.extension() + " extension.");
        }
    }

    /**
     * Keeps only the final path segment (browsers on some systems send a full
     * path), drops control characters, and bounds the length while keeping the
     * extension.
     */
    static String cleanName(String originalName) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "attachment";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
        }
        return name;
    }

    private static String describeAllowed() {
        String[] names = ALLOWED.keySet().stream().map(e -> e.toUpperCase(Locale.ROOT)).toArray(String[]::new);
        return String.join(", ", Arrays.copyOf(names, names.length - 1)) + " and " + names[names.length - 1];
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithText(byte[] head, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        return head.length >= offset + expected.length
            && Arrays.equals(head, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static boolean containsNul(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
