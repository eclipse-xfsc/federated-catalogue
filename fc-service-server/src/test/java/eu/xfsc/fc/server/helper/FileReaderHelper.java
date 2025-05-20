package eu.xfsc.fc.server.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileReaderHelper {
    public static String getMockFileDataAsString(String filename) throws IOException {
        Path resourceDirectory = Paths.get("src", "test", "resources", "mock-data");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath();
        return new String(Files.readAllBytes(Paths.get(absolutePath + "/" + filename )));
    }
}