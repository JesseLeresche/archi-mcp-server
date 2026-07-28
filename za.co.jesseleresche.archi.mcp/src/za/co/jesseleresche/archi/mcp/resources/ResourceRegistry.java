package za.co.jesseleresche.archi.mcp.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of static MCP resources (design guides, reference documents).
 *
 * <p>Resources are Markdown files bundled inside the plugin JAR under
 * {@code resources/guides/}. They are exposed via the MCP
 * {@code resources/list} and {@code resources/read} methods so that any
 * MCP-compliant client can fetch them without out-of-band knowledge of
 * the file system.
 *
 * <p>Agents should fetch the relevant guide before executing complex
 * multi-step operations (e.g. creating a Layered View) to ensure they
 * follow the correct element types, layer positions, relationship rules,
 * and tool-call sequence.
 */
public class ResourceRegistry {

    /**
     * Immutable descriptor for a single registered resource.
     */
    public record ResourceDescriptor(
            /** Unique URI used in resources/read requests. */
            String uri,
            /** Human-readable name shown in resources/list. */
            String name,
            /** One-line description shown in resources/list. */
            String description,
            /** MIME type — always "text/markdown" for guide files. */
            String mimeType,
            /** Classpath path used to load the content at runtime. */
            String classpathPath
    ) {}

    // ── registered resources ────────────────────────────────────────────────

    private static final List<ResourceDescriptor> RESOURCES;

    static {
        List<ResourceDescriptor> list = new ArrayList<>();

        list.add(new ResourceDescriptor(
                "archi://guides/layered-view",
                "Layered View — Agent Generation Guide",
                "Complete guide for creating ArchiMate Layered Views: layer definitions, "
                        + "element types, relationship rules, layout positions, styling, "
                        + "MCP tool-call sequence, and a worked example. "
                        + "Fetch this resource before creating a Layered View.",
                "text/markdown",
                "/resources/guides/layered-view-agent-guide.md"
        ));

        list.add(new ResourceDescriptor(
                "archi://guides/application-structure-view",
                "Application Structure View — Agent Generation Guide",
                "Complete guide for creating ArchiMate Application Structure Views: decomposing "
                        + "a composite Application Component into sub-components, element types, "
                        + "relationship rules, nesting, styling, MCP tool-call sequence, and a "
                        + "worked example. Fetch this resource before creating an Application "
                        + "Structure View.",
                "text/markdown",
                "/resources/guides/application-structure-view-agent-guide.md"
        ));

        list.add(new ResourceDescriptor(
                "archi://guides/data-model-view",
                "Data Model View — Agent Generation Guide",
                "Complete guide for creating ArchiMate Data Model Views: nesting Data Objects "
                        + "inside a parent store, element types, relationship rules, styling, "
                        + "MCP tool-call sequence, and a worked example. Fetch this resource "
                        + "before creating a Data Model View.",
                "text/markdown",
                "/resources/guides/data-model-view-agent-guide.md"
        ));

        list.add(new ResourceDescriptor(
                "archi://guides/infrastructure-view",
                "Infrastructure View — Agent Generation Guide",
                "Complete guide for creating ArchiMate Infrastructure Views: Node composition "
                        + "from System Software and Device parts, realization of Technology "
                        + "Services and Artifacts, element types, relationship rules, styling, "
                        + "MCP tool-call sequence, and a worked example. Fetch this resource "
                        + "before creating an Infrastructure View.",
                "text/markdown",
                "/resources/guides/infrastructure-view-agent-guide.md"
        ));

        RESOURCES = Collections.unmodifiableList(list);
    }

    // ── singleton ────────────────────────────────────────────────────────────

    private static final ResourceRegistry INSTANCE = new ResourceRegistry();

    public static ResourceRegistry getInstance() {
        return INSTANCE;
    }

    private ResourceRegistry() {}

    // ── public API ───────────────────────────────────────────────────────────

    /** All registered resource descriptors (for resources/list). */
    public List<ResourceDescriptor> list() {
        return RESOURCES;
    }

    /**
     * Read the content of a resource by URI (for resources/read).
     *
     * @param uri  the resource URI as returned by {@link #list()}
     * @return the resource text content
     * @throws IllegalArgumentException if the URI is not registered
     * @throws IOException              if the classpath resource cannot be read
     */
    public String read(String uri) throws IOException {
        ResourceDescriptor descriptor = RESOURCES.stream()
                .filter(r -> r.uri().equals(uri))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown resource URI: " + uri
                                + ". Call resources/list to see available resources."));

        try (InputStream is = ResourceRegistry.class.getResourceAsStream(descriptor.classpathPath())) {
            if (is == null) {
                throw new IOException(
                        "Bundled resource not found on classpath: " + descriptor.classpathPath());
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
