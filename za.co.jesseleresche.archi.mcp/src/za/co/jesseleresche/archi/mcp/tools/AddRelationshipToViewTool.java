package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Draws a visual connection on a view for an existing logical relationship.
 * Both source and target elements must already be present as figures on the view.
 */
public class AddRelationshipToViewTool implements ITool {

    @Override
    public String getName() {
        return "add_relationship_to_view";
    }

    @Override
    public String getDescription() {
        return "Draw a visual connection on a view for an existing logical relationship. "
                + "Both source and target elements must already be present as figures on the view.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the target view");

        ObjectNode relationshipId = properties.putObject("relationship_id");
        relationshipId.put("type", "string");
        relationshipId.put("description", "ID of the logical relationship to draw");

        ObjectNode sourceFigureId = properties.putObject("source_figure_id");
        sourceFigureId.put("type", "string");
        sourceFigureId.put("description",
                "Optional: figure ID of the source element on the view. If omitted, the tool finds it automatically.");

        ObjectNode targetFigureId = properties.putObject("target_figure_id");
        targetFigureId.put("type", "string");
        targetFigureId.put("description",
                "Optional: figure ID of the target element on the view. If omitted, the tool finds it automatically.");

        ObjectNode bendpoints = properties.putObject("bendpoints");
        bendpoints.put("type", "array");
        bendpoints.put("description",
                "Optional list of bendpoints for right-angle or multi-segment routing. "
                        + "Each bendpoint specifies offsets from the source and target figure centres.");
        ObjectNode bpItems = bendpoints.putObject("items");
        bpItems.put("type", "object");
        ObjectNode bpProps = bpItems.putObject("properties");
        bpProps.putObject("startX").put("type", "integer");
        bpProps.putObject("startY").put("type", "integer");
        bpProps.putObject("endX").put("type", "integer");
        bpProps.putObject("endY").put("type", "integer");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");
        required.add("relationship_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        // Step 1: Get open model
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        String relationshipId = args.get("relationship_id").asText();
        String sourceFigureId = args.has("source_figure_id") ? args.get("source_figure_id").asText() : null;
        String targetFigureId = args.has("target_figure_id") ? args.get("target_figure_id").asText() : null;
        JsonNode bendpointsNode = args.has("bendpoints") ? args.get("bendpoints") : null;

        // Step 2: Find view
        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        // Step 3: Find relationship
        IArchimateRelationship relationship = ModelAccessor.findRelationshipById(model, relationshipId);
        if (relationship == null) {
            throw new Exception("Relationship not found: " + relationshipId);
        }

        // Step 4: Resolve source figure
        IDiagramModelArchimateObject sourceFigure;
        if (sourceFigureId != null) {
            sourceFigure = ModelAccessor.findFigureById(view, sourceFigureId);
        } else {
            sourceFigure = ModelAccessor.findFigureByElementId(view, relationship.getSource().getId());
        }
        if (sourceFigure == null) {
            throw new Exception("Source element '" + relationship.getSource().getName()
                    + "' is not present as a figure on view '" + view.getName()
                    + "'. Call add_element_to_view first.");
        }

        // Step 5: Resolve target figure
        IDiagramModelArchimateObject targetFigure;
        if (targetFigureId != null) {
            targetFigure = ModelAccessor.findFigureById(view, targetFigureId);
        } else {
            targetFigure = ModelAccessor.findFigureByElementId(view, relationship.getTarget().getId());
        }
        if (targetFigure == null) {
            throw new Exception("Target element '" + relationship.getTarget().getName()
                    + "' is not present as a figure on view '" + view.getName()
                    + "'. Call add_element_to_view first.");
        }

        // Step 6: Check for existing connection
        IDiagramModelArchimateConnection existing =
                ModelAccessor.findConnectionByRelationshipId(view, relationshipId);
        if (existing != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connection_id", existing.getId());
            result.put("already_exists", true);
            return ToolRegistry.MAPPER.writeValueAsString(result);
        }

        // Step 7: Create connection on UI thread
        // CRITICAL: Use connection.connect(source, target) - NEVER view.getChildren().add(connection)
        final IDiagramModelArchimateConnection[] connectionHolder = {null};

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IDiagramModelArchimateConnection connection =
                    IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
            connection.setArchimateRelationship(relationship);

            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);

            Command cmd = new Command("Add Relationship to View") {
                @Override
                public void execute() {
                    connection.connect(sourceFigure, targetFigure);

                    if (bendpointsNode != null && bendpointsNode.isArray()) {
                        for (JsonNode bp : bendpointsNode) {
                            IDiagramModelBendpoint bendpoint =
                                    IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
                            bendpoint.setStartX(bp.path("startX").asInt(0));
                            bendpoint.setStartY(bp.path("startY").asInt(0));
                            bendpoint.setEndX(bp.path("endX").asInt(0));
                            bendpoint.setEndY(bp.path("endY").asInt(0));
                            connection.getBendpoints().add(bendpoint);
                        }
                    }
                }

                @Override
                public void undo() {
                    connection.disconnect();
                }
            };

            if (stack != null) {
                stack.execute(cmd);
            } else {
                cmd.execute();
            }

            connectionHolder[0] = connection;

            IEditorModelManager.INSTANCE.saveModel(model);

            // Step 8: Return result
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("connection_id", connection.getId());
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}
