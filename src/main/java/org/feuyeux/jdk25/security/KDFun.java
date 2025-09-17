package org.feuyeux.jdk25.security;

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

/// JEP 510: Key Derivation Function API
public class KDFun {

    public SecretKey generateDerivedKey(SecretKey secretKey, byte[] salt, byte[] info) throws Exception {
        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        AlgorithmParameterSpec params =
                HKDFParameterSpec.ofExtract()
                        .addIKM(secretKey)
                        .addSalt(salt).thenExpand(info, 32);
        return hkdf.deriveKey("AES", params);
    }

    public Object generateEntropicData(AlgorithmParameterSpec parameterSpec) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KDF hkdf = KDF.getInstance("HKDF-SHA256");
        return hkdf.deriveData(parameterSpec);
    }

    void main()  {
        KDFun kdfFun = new KDFun();

        // 创建输入密钥材料 (IKM)
        byte[] ikmBytes = "my-secret-key-material".getBytes();
        SecretKey ikm = new SecretKeySpec(ikmBytes, "RAW");

        // 创建盐值
        byte[] salt = "random-salt".getBytes();

        // 创建应用特定信息
        byte[] info = "application-context".getBytes();

        System.out.println("=== JEP 510: Key Derivation Function API Demo ===");
        System.out.println("(Simulated using HMAC-based HKDF implementation)");
        System.out.println();
        System.out.println("Original IKM: " + bytesToHex(ikmBytes));
        System.out.println("Salt: " + bytesToHex(salt));
        System.out.println("Info: " + bytesToHex(info));
        System.out.println();

        // 派生密钥
        try {
            SecretKey derivedKey = kdfFun.generateDerivedKey(ikm, salt, info);
            System.out.println("✓ Key derivation successful!");
            System.out.println("Derived Key Algorithm: " + derivedKey.getAlgorithm());
            System.out.println("Derived Key Length: " + derivedKey.getEncoded().length + " bytes");
            System.out.println("Derived Key: " + bytesToHex(derivedKey.getEncoded()));

            // 演示使用不同的 info 生成不同的密钥
            byte[] differentInfo = "different-context".getBytes();
            SecretKey derivedKey2 = kdfFun.generateDerivedKey(ikm, salt, differentInfo);
            System.out.println();
            System.out.println("With different info '" + new String(differentInfo) + "':");
            System.out.println("Different Derived Key: " + bytesToHex(derivedKey2.getEncoded()));

            AlgorithmParameterSpec params = HKDFParameterSpec.ofExtract().addIKM(ikm).addSalt(salt).thenExpand(info, 16);
            Object entropicData = kdfFun.generateEntropicData(params);
            System.out.println();
            System.out.println("Entropic Data: " + bytesToHex((byte[]) entropicData));
        } catch (Exception e) {
            System.out.println("✗ Key derivation failed: " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
