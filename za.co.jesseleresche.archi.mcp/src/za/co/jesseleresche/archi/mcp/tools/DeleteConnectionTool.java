package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Deletes a visual connection from a view. Only removes the connection figure —
 * the underlying logical relationship in the model is preserved.
 */
public class DeleteConnectionTool implements ITool {

    @Override
    public String getName() {
        return "delete_connection";
    }

    @Override
    public String getDescription() {
        return "Delete a visual connection from a view. The underlying logical relationship "
                + "in the model is preserved — only the visual connection on the view is removed. "
                + "Identify the connection by connection_id or by relationship_id + view_id.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view containing the connection");

        ObjectNode connectionId = properties.putObject("connection_id");
        connectionId.put("type", "string");
        connectionId.put("description",
                "ID of the visual connection to delete (alternative to relationship_id)");

        ObjectNode relationshipId = properties.putObject("relationship_id");
        relationshipId.put("type", "string");
        relationshipId.put("description",
                "ID of the logical relationship whose visual connection to delete "
                        + "(alternative to connection_id)");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        String connectionId = args.has("connection_id")
                ? args.get("connection_id").asText() : null;
        String relationshipId = args.has("relationship_id")
                ? args.get("relationship_id").asText() : null;

        if (connectionId == null && relationshipId == null) {
            throw new Exception("Either connection_id or relationship_id must be provided");
        }

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        IDiagramModelArchimateConnection connection;
        if (connectionId != null) {
            connection = ModelAccessor.findConnectionById(view, connectionId);
            if (connection == null) {
                throw new Exception("Connection not found on view: " + connectionId);
            }
        } else {
            connection = ModelAccessor.findConnectionByRelationshipId(view, relationshipId);
            if (connection == null) {
                throw new Exception("No connection found for relationship on view: " + relationshipId);
            }
        }

        IDiagramModelArchimateConnection conn = connection;
        String deletedConnectionId = connection.getId();
        String deletedRelationshipId = connection.getArchimateRelationship().getId();
        String deletedRelationshipName = connection.getArchimateRelationship().getName();

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            EcoreUtil.delete(conn, true);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("connection_id", deletedConnectionId);
            entry.put("relationship_id", deletedRelationshipId);
            entry.put("relationship_name", deletedRelationshipName);
            entry.put("view_id", viewId);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}
