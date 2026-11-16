package za.co.jesseleresche.archi.mcp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExchangeDirTest {

    @TempDir
    Path tmp;
    Path root;

    @BeforeEach
    void setRoot() {
        root = tmp.resolve("exchange");
        System.setProperty(ExchangeDir.PROPERTY, root.toString());
    }

    @AfterEach
    void clearRoot() {
        System.clearProperty(ExchangeDir.PROPERTY);
    }

    private static void rejects(String name, String why) {
        Exception e = assertThrows(Exception.class, () -> ExchangeDir.resolve(name), name);
        assertTrue(e.getMessage().contains(why), name + ": " + e.getMessage());
    }

    @Test
    void resolvesBareJsonNamesInsideRootCreatingIt() throws Exception {
        Path p = ExchangeDir.resolve("view-1.json");
        assertEquals(root.toRealPath().resolve("view-1.json"), p);
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void rejectsPathsAndNonJsonNames() {
        rejects("../x.json", "bare .json file name");
        rejects("/etc/passwd.json", "bare .json file name");
        rejects("a/b.json", "bare .json file name");
        rejects("a\\b.json", "bare .json file name");
        rejects("x.txt", "bare .json file name");
        rejects(".json", "bare .json file name");
        rejects("..json", "bare .json file name");
        rejects("", "bare .json file name");
    }

    @Test
    void rejectsSymlinks() throws Exception {
        Files.createDirectories(root);
        Path outside = Files.writeString(tmp.resolve("secret.json"), "[]");
        Files.createSymbolicLink(root.resolve("link.json"), outside);
        Exception e = assertThrows(Exception.class, () -> ExchangeDir.read("link.json"));
        assertTrue(e.getMessage().contains("symbolic link"), e.getMessage());
    }

    @Test
    void writesNewFilesOnlyAndReadsThemBack() throws Exception {
        Path p = ExchangeDir.write("out.json", "{\"a\":1}");
        assertEquals("{\"a\":1}", Files.readString(p));
        assertEquals(1, ExchangeDir.read("out.json").get("a").asInt());
        Exception e = assertThrows(Exception.class, () -> ExchangeDir.write("out.json", "{}"));
        assertTrue(e.getMessage().contains("already exists"), e.getMessage());
        assertEquals("{\"a\":1}", Files.readString(p));
    }

    @Test
    void readReportsMissingAndInvalidFiles() throws Exception {
        Exception e = assertThrows(Exception.class, () -> ExchangeDir.read("nope.json"));
        assertTrue(e.getMessage().contains("not found in the exchange folder"), e.getMessage());
        Files.createDirectories(root);
        Files.writeString(root.resolve("bad.json"), "{nope");
        e = assertThrows(Exception.class, () -> ExchangeDir.read("bad.json"));
        assertTrue(e.getMessage().contains("not valid JSON"), e.getMessage());
    }

    @Test
    void readRejectsOversizedFiles() throws Exception {
        Files.createDirectories(root);
        Files.write(root.resolve("big.json"), new byte[(int) ExchangeDir.MAX_READ_BYTES + 1]);
        Exception e = assertThrows(Exception.class, () -> ExchangeDir.read("big.json"));
        assertTrue(e.getMessage().contains("too large"), e.getMessage());
    }
}
