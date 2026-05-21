package com.songhg.veri.agent.common.secret;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class LocalSecretCipher {

    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private LocalSecretCipher() {
    }

    public static EncryptedMaterial encrypt(String value, SecretProviderProperties properties) {
        String version = configuredMasterKeyVersion(properties);
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey(properties, version), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] cipherText = Arrays.copyOf(encrypted, encrypted.length - GCM_TAG_BYTES);
            byte[] authTag = Arrays.copyOfRange(encrypted, encrypted.length - GCM_TAG_BYTES, encrypted.length);
            return new EncryptedMaterial(
                    Base64.getEncoder().encodeToString(cipherText),
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(authTag),
                    "AES-256-GCM",
                    version
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "本地密文加密失败");
        }
    }

    public static String decrypt(
            String cipherText,
            String iv,
            String authTag,
            String algorithm,
            String masterKeyVersion,
            SecretProviderProperties properties,
            String secretRef
    ) {
        try {
            if (!"AES-256-GCM".equalsIgnoreCase(algorithm)) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "暂不支持的本地密钥算法: " + algorithm);
            }
            byte[] decodedCipherText = decodeMaterial(cipherText);
            byte[] decodedAuthTag = decodeMaterial(authTag);
            byte[] cipherTextWithTag = new byte[decodedCipherText.length + decodedAuthTag.length];
            System.arraycopy(decodedCipherText, 0, cipherTextWithTag, 0, decodedCipherText.length);
            System.arraycopy(decodedAuthTag, 0, cipherTextWithTag, decodedCipherText.length, decodedAuthTag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(properties, masterKeyVersion), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, decodeMaterial(iv)));
            return new String(cipher.doFinal(cipherTextWithTag), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "本地密文解密失败: " + secretRef);
        }
    }

    public static String configuredMasterKeyVersion(SecretProviderProperties properties) {
        return StringUtils.hasText(properties.localMasterKeyVersion()) ? properties.localMasterKeyVersion().trim() : "v1";
    }

    private static byte[] masterKey(SecretProviderProperties properties, String requiredVersion) {
        String configuredVersion = configuredMasterKeyVersion(properties);
        if (StringUtils.hasText(requiredVersion) && !requiredVersion.trim().equals(configuredVersion)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "本地密钥版本不匹配: " + requiredVersion);
        }
        String rawKey = properties.localMasterKey();
        if (!StringUtils.hasText(rawKey)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "缺少 WP1_LOCAL_SECRET_MASTER_KEY");
        }
        byte[] decoded = decodeKey(rawKey.trim());
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "WP1_LOCAL_SECRET_MASTER_KEY 必须为 32 字节 AES-256 密钥");
        }
        return decoded;
    }

    private static byte[] decodeKey(String value) {
        List<java.util.function.Function<String, byte[]>> decoders = List.of(
                LocalSecretCipher::decodeHexIfPossible,
                item -> Base64.getDecoder().decode(item),
                item -> item.getBytes(StandardCharsets.UTF_8)
        );
        for (var decoder : decoders) {
            try {
                byte[] decoded = decoder.apply(value);
                if (decoded.length == AES_256_KEY_BYTES) {
                    return decoded;
                }
            } catch (Exception ignored) {
                // Try the next supported encoding.
            }
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeMaterial(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "密文材料不完整");
        }
        String normalized = value.trim();
        try {
            return decodeHexIfPossible(normalized);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(normalized);
        }
    }

    private static byte[] decodeHexIfPossible(String value) {
        if (value.length() % 2 != 0 || !value.matches("(?i)^[0-9a-f]+$")) {
            throw new IllegalArgumentException("not hex");
        }
        return HexFormat.of().parseHex(value);
    }

    public record EncryptedMaterial(
            String cipherText,
            String iv,
            String authTag,
            String algorithm,
            String masterKeyVersion
    ) {
    }
}
