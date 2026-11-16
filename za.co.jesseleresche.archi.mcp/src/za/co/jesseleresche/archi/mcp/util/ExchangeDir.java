package za.co.jesseleresche.archi.mcp.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.protocol.ObjectMapperHolder;

/**
 * The only folder MCP clients can read batch payloads from ({@code items_file}) or write view
 * definitions to ({@code output_file}). The server is unauthenticated on localhost, so callers
 * name a bare {@code .json} file, never a path: anything else, anything resolving outside the
 * folder, and symbolic links are rejected, and writes never overwrite.
 *
 * <p>Root: {@code ~/.archi-mcp/exchange}, or the {@value #PROPERTY} system property.
 */
public final class ExchangeDir {

    public static final String PROPERTY = "archi.mcp.exchange.dir";

    /** Largest file {@link #read} accepts. */
    public static final long MAX_READ_BYTES = 20L * 1024 * 1024;

    private static final String NAME = "[A-Za-z0-9_-][A-Za-z0-9._-]*\\.json";

    private ExchangeDir() {
    }

    /** The exchange folder (created on demand), as a real path. */
    public static Path root() throws IOException {
        String override = System.getProperty(PROPERTY);
        Path root = override != null && !override.isBlank()
                ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".archi-mcp", "exchange");
        Files.createDirectories(root);
        return root.toRealPath();
    }

    /**
     * Resolves a bare file name inside the exchange folder.
     *
     * @throws Exception if the name is not a bare .json file name or resolves outside the folder
     */
    public static Path resolve(String name) throws Exception {
        if (name == null || !name.matches(NAME)) {
            throw new Exception("'" + name + "' must be a bare .json file name (letters, digits, . _ -) "
                    + "in the exchange folder, not a path");
        }
        Path root = root();
        Path p = root.resolve(name).normalize();
        if (!root.equals(p.getParent())) {
            throw new Exception("'" + name + "' resolves outside the exchange folder");
        }
        if (Files.isSymbolicLink(p)) {
            throw new Exception("'" + name + "' is a symbolic link; the exchange folder only accepts regular files");
        }
        return p;
    }

    /** Reads and parses a JSON file from the exchange folder. */
    public static JsonNode read(String name) throws Exception {
        Path p = resolve(name);
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new Exception("'" + name + "' not found in the exchange folder " + root());
        }
        if (Files.size(p) > MAX_READ_BYTES) {
            throw new Exception("'" + name + "' is too large (limit " + MAX_READ_BYTES / (1024 * 1024) + " MB)");
        }
        try {
            return ObjectMapperHolder.get().readTree(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Exception("'" + name + "' is not valid JSON: " + e.getMessage());
        }
    }

    /** Writes a new file to the exchange folder; an existing file is never overwritten. */
    public static Path write(String name, String content) throws Exception {
        Path p = resolve(name);
        try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (FileAlreadyExistsException e) {
            throw new Exception("'" + name + "' already exists in the exchange folder; choose a new name");
        } catch (NoSuchFileException e) {
            throw new Exception("exchange folder is missing: " + e.getMessage());
        }
        return p;
    }
}
