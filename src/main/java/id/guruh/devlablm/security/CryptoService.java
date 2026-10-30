package id.guruh.devlablm.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public CryptoService() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".devlablm");
            Path keyFile = dir.resolve("secret.key");
            Files.createDirectories(dir);

            if (Files.notExists(keyFile)) {
                byte[] newKey = new byte[32]; // AES-256
                random.nextBytes(newKey);
                Files.writeString(keyFile, Base64.getEncoder().encodeToString(newKey));
                try {
                    Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException ignored) {
                    // Windows: permission POSIX tidak didukung
                }
            }

            byte[] raw = Base64.getDecoder().decode(Files.readString(keyFile).trim());
            this.key = new SecretKeySpec(raw, "AES");
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menyiapkan kunci enkripsi", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length);
            buf.put(iv).put(enc);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Gagal mengenkripsi", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return encoded;
        try {
            ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);
            byte[] enc = new byte[buf.remaining()];
            buf.get(enc);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal mendekripsi (kunci berubah atau data rusak)", e);
        }
    }
}