package com.oogle.digitalid.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Pem {
    public static void write(Path path, String pem) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, pem);
    }
    public static String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
