package za.co.jesseleresche.archi.mcp.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;

/**
 * Exports an ArchiMate view as a PNG image.
 * Returns the image as an MCP image content block (base64-encoded).
 * Optionally saves to a file path on disk.
 */
public class ExportViewAsImageTool implements ITool {

    @Override
    public String getName() {
        return "export_view_as_image";
    }

    @Override
    public String getDescription() {
        return "Export a view as a PNG image. Returns the image inline and "
                + "optionally saves it to a file path on disk.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to export");

        ObjectNode scale = properties.putObject("scale");
        scale.put("type", "number");
        scale.put("description",
                "Scale factor for the exported image (default 1.0). "
                        + "Use 2.0 for retina/high-DPI output.");

        ObjectNode margin = properties.putObject("margin");
        margin.put("type", "integer");
        margin.put("description",
                "Margin in pixels around the diagram (default 10)");

        ObjectNode outputPath = properties.putObject("output_path");
        outputPath.put("type", "string");
        outputPath.put("description",
                "Optional: absolute file path to save the PNG to (e.g. "
                        + "\"/Users/me/diagram.png\"). Parent directory must exist.");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        // Not used directly — executeWithContent handles this tool's output
        throw new UnsupportedOperationException(
                "Use executeWithContent for image export");
    }

    @Override
    public List<ObjectNode> executeWithContent(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        double scale = args.has("scale") ? args.get("scale").asDouble() : 1.0;
        int margin = args.has("margin") ? args.get("margin").asInt() : 10;
        String outputPath = args.has("output_path")
                ? args.get("output_path").asText() : null;

        if (scale <= 0 || scale > 4.0) {
            throw new Exception(
                    "Scale must be between 0 (exclusive) and 4.0");
        }

        IArchimateDiagramModel view =
                ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        byte[] pngBytes = UiThreadUtil.syncExec(() -> {
            Image image = DiagramUtils.createImage(view, scale, margin);
            try {
                ImageLoader loader = new ImageLoader();
                loader.data = new ImageData[]{image.getImageData()};
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                loader.save(baos, SWT.IMAGE_PNG);
                return baos.toByteArray();
            } finally {
                image.dispose();
            }
        });

        // Save to disk if requested
        if (outputPath != null) {
            File file = new File(outputPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                throw new Exception(
                        "Parent directory does not exist: " + parent);
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(pngBytes);
            }
        }

        String base64 = Base64.getEncoder().encodeToString(pngBytes);

        List<ObjectNode> blocks = new ArrayList<>();

        // Image content block
        ObjectNode imageBlock = ToolRegistry.MAPPER.createObjectNode();
        imageBlock.put("type", "image");
        imageBlock.put("data", base64);
        imageBlock.put("mimeType", "image/png");
        blocks.add(imageBlock);

        // Text content block with metadata
        ObjectNode textBlock = ToolRegistry.MAPPER.createObjectNode();
        textBlock.put("type", "text");
        StringBuilder info = new StringBuilder();
        info.append("Exported view \"").append(view.getName())
                .append("\" as PNG (")
                .append(String.format("%.0f", scale * 100)).append("% scale)");
        if (outputPath != null) {
            info.append(". Saved to: ").append(outputPath);
        }
        textBlock.put("text", info.toString());
        blocks.add(textBlock);

        return blocks;
    }
}
