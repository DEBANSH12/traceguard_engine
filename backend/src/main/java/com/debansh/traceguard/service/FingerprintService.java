package com.debansh.traceguard.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
public class FingerprintService {

    /**
     * Normalizes volatile memory addresses and lambda suffixes from the top 5 stack frames
     * and computes a deterministic SHA-256 hex digest.
     */
    public String computeFingerprint(String serviceName, String exceptionType, String endpointPath, String rawStackTrace) {
        String normalizedFrames = Arrays.stream(rawStackTrace.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("at ") || line.contains("Exception"))
                .limit(5)
                .map(line -> line.replaceAll("\\$\\$Lambda\\$[0-9a-fA-Fx/]+", "\\$\\$Lambda")
                                 .replaceAll("@[0-9a-fA-F]{5,}", "@ADDR"))
                .collect(Collectors.joining("|"));

        String canonicalInput = String.join("::",
                serviceName.trim().toLowerCase(),
                exceptionType.trim(),
                endpointPath.trim(),
                normalizedFrames);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
