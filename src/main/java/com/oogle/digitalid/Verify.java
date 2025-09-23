package com.oogle.digitalid;

import com.oogle.digitalid.service.IdIssuer;
import java.nio.file.Path;

public class Verify {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java -cp build/libs/digital-id-gui-*.jar com.oogle.digitalid.Verify <path/to/ID.did.json>");
            System.exit(1);
        }
        var issuer = new IdIssuer(Path.of("keys"), "Oogle ID Authority");
        boolean ok = issuer.verify(Path.of(args[0]));
        System.out.println(ok ? "VALID ✅" : "INVALID ❌");
        System.exit(ok ? 0 : 2);
    }
}
