package com.oogle.digitalid.crypto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public final class Crypto {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String b64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
    public static byte[] b64UrlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    public static KeyPair generateRsa2048() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toPemPrivate(Key privateKey) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }
    public static String toPemPublic(Key publicKey) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static PrivateKey privateFromPem(String pem) {
        try {
            String b64 = pem.replaceAll("-----\\w+ PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(b64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Invalid private key PEM", e);
        }
    }
    public static PublicKey publicFromPem(String pem) {
        try {
            String b64 = pem.replaceAll("-----\\w+ PUBLIC KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(b64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Invalid public key PEM", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] signSha256Rsa(byte[] data, PrivateKey privateKey) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(privateKey);
            s.update(data);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verifySha256Rsa(byte[] data, byte[] sig, PublicKey publicKey) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(publicKey);
            s.update(data);
            return s.verify(sig);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String packAsCompactJws(String canonicalPayloadJson, PrivateKey privateKey) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String h = b64Url(header.getBytes(StandardCharsets.UTF_8));
        String p = b64Url(canonicalPayloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = h + "." + p;
        byte[] sig = signSha256Rsa(signingInput.getBytes(StandardCharsets.UTF_8), privateKey);
        String s = b64Url(sig);
        return signingInput + "." + s;
    }

    public static boolean verifyCompactJws(String jws, PublicKey publicKey) {
        String[] parts = jws.split("\\.");
        if (parts.length != 3) return false;
        String signingInput = parts[0] + "." + parts[1];
        byte[] sig = b64UrlDecode(parts[2]);
        return verifySha256Rsa(signingInput.getBytes(StandardCharsets.UTF_8), sig, publicKey);
    }

    public static String canonicalize(Object obj) {
        return GSON.toJson(obj);
    }
}
