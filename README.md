# Digital ID GUI (Java + JavaFX) — Complete

This is the **full UI** build. It launches a JavaFX window with two screens:
- **Issue**: enter details, generate JSON + QR + ID Card PNG
- **Verify**: pick a `.did.json` and verify

## Requirements
- Java 17+ (Temurin recommended)
- Gradle

## Run
```bash
gradle run
```

## Outputs
- `keys/issuer_private.pem`, `keys/issuer_public.pem` (first run)
- `output/<UUID>.did.json`
- `output/<UUID>.qr.png`
- `output/<UUID>.card.png` (polished card)

## Optional: clickable QR
In `IssueController.java`, set a verify URL:
```java
new IdIssuer(Path.of("keys"), "Oogle ID Authority", "https://yourdomain/verify");
```
Then QR encodes `https://yourdomain/verify?jws=<URLENCODED>`.
