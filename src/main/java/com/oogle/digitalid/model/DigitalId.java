package com.oogle.digitalid.model;

import java.time.Instant;
import java.util.Objects;

public class DigitalId {
    public String id;
    public String fullName;
    public String dateOfBirth;
    public String email;
    public String phone;

    public String issuer;
    public long issuedAt;
    public Long expiresAt;

    public String payloadHash;
    public String signatureAlg;
    public String signature;

    public String jwsCompact;

    public transient String canonicalPayload;

    public static DigitalId basic(String id, String fullName, String dateOfBirth,
                                  String email, String phone,
                                  String issuer, long issuedAt, Long expiresAt) {
        DigitalId d = new DigitalId();
        d.id = id;
        d.fullName = fullName;
        d.dateOfBirth = dateOfBirth;
        d.email = email;
        d.phone = phone;
        d.issuer = issuer;
        d.issuedAt = issuedAt;
        d.expiresAt = expiresAt;
        return d;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().getEpochSecond() > expiresAt;
    }

    @Override public String toString() {
        return "DigitalId{" + id + " " + fullName + "}";
    }

    @Override public int hashCode() { return Objects.hash(id); }
    @Override public boolean equals(Object o) {
        if (!(o instanceof DigitalId)) return false;
        return Objects.equals(id, ((DigitalId)o).id);
    }
}
