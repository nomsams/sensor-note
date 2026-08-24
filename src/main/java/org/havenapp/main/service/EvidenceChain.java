package org.havenapp.main.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class EvidenceChain {
    private EvidenceChain() {
    }

    static String previousHash(File directory) {
        File manifest = new File(directory, "chain.txt");
        if (!manifest.exists()) return "GENESIS";
        try (FileInputStream input = new FileInputStream(manifest)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            String text = output.toString("UTF-8").trim();
            int lastNewline = text.lastIndexOf('\n');
            String line = lastNewline < 0 ? text : text.substring(lastNewline + 1);
            return line.split("\\|")[0];
        } catch (Exception ignored) {
            return "GENESIS";
        }
    }

    static synchronized void append(File directory, String filename) {
        try {
            File file = new File(directory, filename);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(previousHash(directory).getBytes(StandardCharsets.UTF_8));
            digest.update(filename.getBytes(StandardCharsets.UTF_8));
            if (file.isFile() && file.length() > 0) {
                try (FileInputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
                }
            } else {
                digest.update(filename.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format("%02x", value));
            long length = file.isFile() ? file.length() : 0L;
            try (RandomAccessFile manifest = new RandomAccessFile(
                    new File(directory, "chain.txt"), "rw")) {
                manifest.seek(manifest.length());
                manifest.writeBytes(hash + "|" + filename + "|" + length +
                        "|" + System.currentTimeMillis() + "\n");
            }
        } catch (Exception ignored) {
        }
    }
}
