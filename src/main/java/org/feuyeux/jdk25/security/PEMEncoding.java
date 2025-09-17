package org.feuyeux.jdk25.security;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class PEMEncoding {
    void main() {
        String pem = """
                -----BEGIN PUBLIC KEY-----
                MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgjDohS0RHP395oJxciVaeks9N
                KNY5m9V1IkBBwYsMGyxskrW5sapgi9qlGSYOma9kkko1xlBs17qG8TTg38faxgGJ
                sLT2BAmdVFwuWdRtzq6ONn2YPHYj5s5pqx6vU5baz58/STQXNIhn21QoPjXgQCnj
                Pp0OxnacWeRSnAIOmQIDAQAB
                -----END PUBLIC KEY-----
                """;

        try {
            String base64 = pem.replaceAll("-----.*-----", "").replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey key = factory.generatePublic(spec);

            System.out.println("Loaded key: " + key.getAlgorithm());


            // 1. Generate an EC key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // 2. Encode public key to PEM
            String publicKeyPEM = PEMEncoder.of()
                    .encodeToString(keyPair.getPublic());
            System.out.println("Public Key PEM:\n" + publicKeyPEM);

            // 3. Encode a private key to encrypted PEM
            char[] password = "secret".toCharArray();
            String privateKeyPEM = PEMEncoder.of()
                    .withEncryption(password)
                    .encodeToString(keyPair.getPrivate());
            System.out.println("Encrypted Private Key PEM:\n" + privateKeyPEM);

            // 4. Decode a public key
            PublicKey decodedPubKey = PEMDecoder.of()
                    .decode(publicKeyPEM, PublicKey.class);
            System.out.println("Decoded Public Key Algo: " + decodedPubKey.getAlgorithm());

            // 5. Decode encrypted private key
            PrivateKey decodedPrivateKey = PEMDecoder.of()
                    .withDecryption(password)
                    .decode(privateKeyPEM, PrivateKey.class);
            System.out.println("Decoded Private Key Algo: " + decodedPrivateKey.getAlgorithm());

        } catch (NoSuchAlgorithmException |
                 InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }
}
