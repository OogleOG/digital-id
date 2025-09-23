package com.oogle.digitalid.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.oogle.digitalid.crypto.Crypto;
import com.oogle.digitalid.model.DigitalId;
import com.oogle.digitalid.util.Pem;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;

public class IdIssuer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Path keyDir;
    private final String issuerName;
    private final String verifyBaseUrl; // optional

    public IdIssuer(Path keyDir, String issuerName) {
        this(keyDir, issuerName, null);
    }

    public IdIssuer(Path keyDir, String issuerName, String verifyBaseUrl) {
        this.keyDir = keyDir;
        this.issuerName = issuerName;
        this.verifyBaseUrl = verifyBaseUrl;
    }

    public void ensureIssuerKeys() throws IOException {
        Path priv = keyDir.resolve("issuer_private.pem");
        Path pub  = keyDir.resolve("issuer_public.pem");
        if (Files.exists(priv) && Files.exists(pub)) return;

        KeyPair kp = Crypto.generateRsa2048();
        Pem.write(priv, Crypto.toPemPrivate(kp.getPrivate()));
        Pem.write(pub,  Crypto.toPemPublic(kp.getPublic()));
        System.out.println("Generated issuer keypair at: " + keyDir.toAbsolutePath());
    }

    public PublicKey getIssuerPublicKey() throws IOException {
        String pem = Pem.read(keyDir.resolve("issuer_public.pem"));
        return Crypto.publicFromPem(pem);
    }

    public PrivateKey getIssuerPrivateKey() throws IOException {
        String pem = Pem.read(keyDir.resolve("issuer_private.pem"));
        return Crypto.privateFromPem(pem);
    }

    public DigitalId issue(String fullName, String dob, String email, String phone, Long expiresAt) throws IOException {
        ensureIssuerKeys();
        long now = Instant.now().getEpochSecond();

        DigitalId d = DigitalId.basic(
                UUID.randomUUID().toString(),
                fullName,
                emptyToNull(dob),
                emptyToNull(email),
                emptyToNull(phone),
                issuerName,
                now,
                expiresAt
        );

        DigitalId copy = DigitalId.basic(d.id, d.fullName, d.dateOfBirth, d.email, d.phone, d.issuer, d.issuedAt, d.expiresAt);
        String canonical = Crypto.canonicalize(copy);
        d.canonicalPayload = canonical;

        byte[] hash = Crypto.sha256(canonical.getBytes(StandardCharsets.UTF_8));
        d.payloadHash = Crypto.b64Url(hash);

        d.signatureAlg = "SHA256withRSA";
        byte[] sig = Crypto.signSha256Rsa(canonical.getBytes(StandardCharsets.UTF_8), getIssuerPrivateKey());
        d.signature = Crypto.b64Url(sig);

        d.jwsCompact = Crypto.packAsCompactJws(canonical, getIssuerPrivateKey());

        return d;
    }

    public Path saveIdFiles(DigitalId d, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Path json = outDir.resolve(d.id + ".did.json");
        Files.writeString(json, GSON.toJson(d));

        // QR payload
        String qrPayload = d.jwsCompact;
        if (verifyBaseUrl != null && !verifyBaseUrl.isBlank()) {
            String enc = URLEncoder.encode(d.jwsCompact, StandardCharsets.UTF_8);
            qrPayload = verifyBaseUrl + (verifyBaseUrl.contains("?") ? "&" : "?") + "jws=" + enc;
        }

        // Plain QR image
        Path qrPng = outDir.resolve(d.id + ".qr.png");
        writeQrPng(qrPayload, 512, qrPng);

        // ID Card image
        Path cardPng = outDir.resolve(d.id + ".card.png");
        writeIdCardPng(d, qrPayload, 1000, 600, cardPng);

        return json;
    }

    public boolean verify(Path jsonPath) throws IOException {
        String json = Files.readString(jsonPath);
        DigitalId d = GSON.fromJson(json, DigitalId.class);
        if (d == null) return false;

        DigitalId copy = DigitalId.basic(d.id, d.fullName, d.dateOfBirth, d.email, d.phone, d.issuer, d.issuedAt, d.expiresAt);
        String canonical = Crypto.canonicalize(copy);

        byte[] expectedHash = Crypto.sha256(canonical.getBytes(StandardCharsets.UTF_8));
        boolean hashOk = Crypto.b64Url(expectedHash).equals(d.payloadHash);

        boolean sigOk = Crypto.verifySha256Rsa(
                canonical.getBytes(StandardCharsets.UTF_8),
                Crypto.b64UrlDecode(d.signature),
                getIssuerPublicKey()
        );

        boolean jwsOk = d.jwsCompact == null || Crypto.verifyCompactJws(d.jwsCompact, getIssuerPublicKey());

        return hashOk && sigOk && jwsOk && !(d.expiresAt != null && d.isExpired());
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void writeQrPng(String text, int size, Path path) throws IOException {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            var matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
            MatrixToImageWriter.writeToPath(matrix, "PNG", path);
        } catch (WriterException e) {
            throw new IOException("Failed to write QR", e);
        }
    }

    private void writeIdCardPng(DigitalId d, String qrPayload, int width, int height, Path out) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g.setColor(new Color(0x0E, 0x11, 0x17));
            g.fillRect(0, 0, width, height);

            // Card plate
            int pad = 30;
            int cardW = width - pad * 2;
            int cardH = height - pad * 2;
            RoundRectangle2D.Float plate = new RoundRectangle2D.Float(pad, pad, cardW, cardH, 28, 28);
            g.setColor(new Color(0x14, 0x18, 0x24));
            g.fill(plate);

            // Left column
            int leftPad = pad + 30;
            int top = pad + 40;

            g.setColor(new Color(0xE6, 0xE7, 0xEA));
            g.setFont(new Font("Segoe UI", Font.BOLD, 34));
            g.drawString("Digital ID", leftPad, top);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            g.setColor(new Color(0x9A, 0xA0, 0xAE));
            g.drawString("Issuer: " + d.issuer, leftPad, top + 26);

            g.setColor(new Color(0x28, 0x30, 0x44));
            g.fillRect(leftPad, top + 40, cardW - 420, 1);

            int y = top + 80;
            int line = 34;
            g.setColor(new Color(0xE6, 0xE7, 0xEA));
            g.setFont(new Font("Segoe UI", Font.BOLD, 22));
            g.drawString("Full Name", leftPad, y); y += 26;
            g.setFont(new Font("Segoe UI", Font.PLAIN, 22));
            g.drawString(nz(d.fullName), leftPad, y); y += line;

            if (d.dateOfBirth != null) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 22));
                g.drawString("Date of Birth", leftPad, y); y += 26;
                g.setFont(new Font("Segoe UI", Font.PLAIN, 22));
                g.drawString(d.dateOfBirth, leftPad, y); y += line;
            }

            if (d.email != null) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 22));
                g.drawString("Email", leftPad, y); y += 26;
                g.setFont(new Font("Segoe UI", Font.PLAIN, 22));
                g.drawString(d.email, leftPad, y); y += line;
            }

            if (d.phone != null) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 22));
                g.drawString("Phone", leftPad, y); y += 26;
                g.setFont(new Font("Segoe UI", Font.PLAIN, 22));
                g.drawString(d.phone, leftPad, y); y += line;
            }

            String issuedStr = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(d.issuedAt), java.time.ZoneOffset.UTC).toLocalDate().toString();
            String expiresStr = (d.expiresAt == null) ? "—" :
                    java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(d.expiresAt), java.time.ZoneOffset.UTC).toLocalDate().toString();

            y += 8;
            g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            g.setColor(new Color(0x9A, 0xA0, 0xAE));
            g.drawString("Issued: " + issuedStr + "   Expires: " + expiresStr, leftPad, y);

            // Right: QR
            int qrSize = 340;
            int qrX = pad + cardW - qrSize - 40;
            int qrY = pad + (cardH - qrSize) / 2;

            var matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(qrPayload, com.google.zxing.BarcodeFormat.QR_CODE, qrSize, qrSize);
            BufferedImage qr = com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(matrix);

            g.setColor(new Color(0x0F, 0x13, 0x20));
            g.fill(new RoundRectangle2D.Float(qrX - 16, qrY - 16, qrSize + 32, qrSize + 32, 20, 20));
            g.drawImage(qr, qrX, qrY, null);

            g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g.setColor(new Color(0x9A, 0xA0, 0xAE));
            String cap = (verifyBaseUrl != null && !verifyBaseUrl.isBlank())
                    ? "Scan to verify online"
                    : "Scan to import";
            int capWidth = g.getFontMetrics().stringWidth(cap);
            g.drawString(cap, qrX + (qrSize - capWidth) / 2, qrY + qrSize + 32);

            g.setFont(new Font("Consolas", Font.PLAIN, 14));
            g.setColor(new Color(0x6B, 0x72, 0x84));
            String idLine = "ID: " + d.id.substring(0, Math.min(12, d.id.length())) + "…";
            g.drawString(idLine, leftPad, pad + cardH - 16);
        } catch (Exception ex) {
            throw new IOException("Failed to render ID card", ex);
        } finally {
            g.dispose();
        }
        javax.imageio.ImageIO.write(img, "PNG", out.toFile());
    }

    private static String nz(String s) { return (s == null || s.isBlank()) ? "—" : s; }
}
