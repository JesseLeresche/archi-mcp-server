package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;

/**
 * Deletes a single logical relationship from the model and removes any visual
 * connections that reference it. The relationship's endpoints are left
 * untouched (unlike {@code delete_element}, which cascades).
 */
public class DeleteRelationshipTool implements ITool {

    @Override
    public String getName() {
        return "delete_relationship";
    }

    @Override
    public String getDescription() {
        return "Delete a single logical relationship from the model. Also "
                + "removes any visual connections that reference it. The "
                + "endpoints are not deleted. Pass dry_run to preview.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("relationship_id").put("type", "string")
                .put("description", "ID of the relationship to delete");
        properties.putObject("dry_run").put("type", "boolean")
                .put("description", "If true, report what would be deleted without deleting");

        ArrayNode required = schema.putArray("required");
        required.add("relationship_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }
        if (!args.has("relationship_id") || args.get("relationship_id").asText().isBlank()) {
            throw new Exception("Missing required field: relationship_id");
        }
        String relId = args.get("relationship_id").asText();
        boolean dryRun = args.has("dry_run") && args.get("dry_run").asBoolean();

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            Map<String, Object> entry = applyDelete(model, relId, dryRun);
            if (!dryRun) {
                IEditorModelManager.INSTANCE.saveModel(model);
            }
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    /**
     * Deletes (or previews deletion of) a relationship. Must run on the UI
     * thread. Does not save the model — the caller is responsible for saving.
     */
    static Map<String, Object> applyDelete(IArchimateModel model, String relId,
            boolean dryRun) throws Exception {
        EObject obj = ArchimateModelUtils.getObjectByID(model, relId);
        if (!(obj instanceof IArchimateRelationship)) {
            throw new Exception("Relationship not found: " + relId);
        }
        IArchimateRelationship rel = (IArchimateRelationship) obj;

        List<IDiagramModelComponent> refs =
                new ArrayList<>(rel.getReferencingDiagramComponents());
        int connections = 0;
        for (IDiagramModelComponent dc : refs) {
            if (dc instanceof IDiagramModelArchimateConnection) {
                connections++;
            }
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("relationship_id", relId);
        entry.put("type", rel.eClass().getName());
        IArchimateConcept src = rel.getSource();
        IArchimateConcept tgt = rel.getTarget();
        entry.put("source_id", src != null ? src.getId() : null);
        entry.put("target_id", tgt != null ? tgt.getId() : null);

        if (dryRun) {
            entry.put("would_delete", true);
            entry.put("connections", connections);
            return entry;
        }

        for (IDiagramModelComponent dc : refs) {
            if (dc instanceof IDiagramModelArchimateConnection) {
                ((IDiagramModelArchimateConnection) dc).disconnect();
            }
        }
        IFolder folder = (IFolder) rel.eContainer();
        if (folder != null) {
            folder.getElements().remove(rel);
        }

        entry.put("deleted", true);
        entry.put("connections_removed", connections);
        return entry;
    }
}
