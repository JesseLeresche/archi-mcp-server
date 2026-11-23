package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;

/**
 * Updates a logical relationship in the model. A type change recreates the
 * relationship as the new type while preserving its ID and reattaching every
 * visual connection that references it. Scalar fields (name, documentation,
 * access type, properties, folder) are applied in place. If a new type is not
 * allowed by the ArchiMate matrix between the existing endpoints the change is
 * still performed but a {@code warning} is returned.
 */
public class UpdateRelationshipTool implements ITool {

    @Override
    public String getName() {
        return "update_relationship";
    }

    @Override
    public String getDescription() {
        return "Update a logical relationship in the model. Type changes "
                + "preserve the relationship's ID and its connections on all "
                + "views, but may invalidate them if the new type isn't allowed "
                + "by the ArchiMate matrix between the existing endpoints "
                + "(returns a warning in that case). On a type change, values the new "
                + "type can't hold (access_type, influence_strength, directed, "
                + "specialization) are dropped and reported in 'dropped'.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        addItemProperties(properties);

        ArrayNode required = schema.putArray("required");
        required.add("relationship_id");

        return schema;
    }

    /** Shared property schema for both the scalar and bulk variants. */
    static void addItemProperties(ObjectNode properties) {
        properties.putObject("relationship_id").put("type", "string")
                .put("description", "ID of the relationship to update");
        properties.putObject("new_type").put("type", "string")
                .put("description",
                        "New relationship type (e.g. 'AssociationRelationship'). "
                                + "Preserves the relationship ID and view connections.");
        properties.putObject("name").put("type", "string");
        properties.putObject("documentation").put("type", "string");
        RelationshipAttributes.addSchema(properties);
        ObjectNode props = properties.putObject("properties");
        props.put("type", "array");
        props.put("description", "Replace custom properties with these key/value pairs");
        ObjectNode propItems = props.putObject("items");
        propItems.put("type", "object");
        ObjectNode propItemProps = propItems.putObject("properties");
        propItemProps.putObject("key").put("type", "string");
        propItemProps.putObject("value").put("type", "string");
        properties.putObject("remove_properties").put("type", "array");
        properties.putObject("specialization").put("type", "string");
        properties.putObject("new_folder_id").put("type", "string")
                .put("description", "Move the relationship to this folder");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            Map<String, Object> entry = applyUpdate(model, args);
            ModelAccessor.saveModel(model);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    /**
     * Applies a single relationship update. Must be called on the UI thread.
     * Does not save the model — the caller is responsible for saving.
     */
    static Map<String, Object> applyUpdate(IArchimateModel model, JsonNode item)
            throws Exception {
        if (!item.has("relationship_id")
                || item.get("relationship_id").asText().isBlank()) {
            throw new Exception("Missing required field: relationship_id");
        }
        String relId = item.get("relationship_id").asText();

        EObject obj = ArchimateModelUtils.getObjectByID(model, relId);
        if (!(obj instanceof IArchimateRelationship)) {
            throw new Exception("Relationship not found: " + relId);
        }
        IArchimateRelationship rel = (IArchimateRelationship) obj;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("relationship_id", relId);

        IArchimateRelationship target = rel;
        String warning = null;

        Map<String, Object> dropped = new LinkedHashMap<>();

        String newType = item.has("new_type") ? item.get("new_type").asText() : null;
        EClass eClass = null;
        if (newType != null && !newType.isBlank()) {
            eClass = ModelAccessor.resolveRelationshipClass(newType);
            if (eClass == null) {
                throw new Exception("Unknown relationship type: " + newType);
            }
        }
        // Judged against the type after new_type, before anything changes.
        RelationshipAttributes.check(eClass != null ? eClass : rel.eClass(), item);
        if (eClass != null) {
            if (!eClass.equals(rel.eClass())) {
                IArchimateConcept src = rel.getSource();
                IArchimateConcept tgt = rel.getTarget();

                if (src != null && tgt != null
                        && !ArchimateModelUtils.isValidRelationship(
                                src.eClass(), tgt.eClass(), eClass)) {
                    warning = "New type " + eClass.getName()
                            + " is not allowed by the ArchiMate matrix between "
                            + src.eClass().getName() + " and "
                            + tgt.eClass().getName()
                            + "; existing view connections may be invalid";
                }

                // Snapshot the visual connections before mutating the model.
                List<IDiagramModelComponent> refs =
                        new ArrayList<>(rel.getReferencingDiagramComponents());

                IArchimateRelationship newRel = (IArchimateRelationship)
                        IArchimateFactory.eINSTANCE.create(eClass);
                newRel.setName(rel.getName());
                newRel.setDocumentation(rel.getDocumentation());
                for (IProperty p : rel.getProperties()) {
                    IProperty np = IArchimateFactory.eINSTANCE.createProperty();
                    np.setKey(p.getKey());
                    np.setValue(p.getValue());
                    newRel.getProperties().add(np);
                }
                newRel.setSource(src);
                newRel.setTarget(tgt);

                // Carry over the old type's attributes where they still apply; report the rest.
                Map<String, Object> old = new LinkedHashMap<>();
                RelationshipAttributes.read(rel, old);
                String spec = ModelAccessor.getSpecialization(rel);
                if (spec != null) {
                    old.put("specialization", spec);
                }
                ObjectNode carry = ToolRegistry.MAPPER.createObjectNode();
                for (Map.Entry<String, Object> e : old.entrySet()) {
                    if (RelationshipAttributes.appliesTo(e.getKey(), eClass)) {
                        carry.set(e.getKey(), ToolRegistry.MAPPER.valueToTree(e.getValue()));
                    } else {
                        dropped.put(e.getKey(), e.getValue());
                    }
                }
                RelationshipAttributes.apply(newRel, carry);

                IFolder folder = (IFolder) rel.eContainer();
                int idx = folder != null ? folder.getElements().indexOf(rel) : -1;
                if (folder != null) {
                    folder.getElements().remove(rel);
                }
                newRel.setId(relId); // preserve the original ID
                if (folder != null) {
                    if (idx >= 0) {
                        folder.getElements().add(idx, newRel);
                    } else {
                        folder.getElements().add(newRel);
                    }
                }

                int reattached = 0;
                for (IDiagramModelComponent dc : refs) {
                    if (dc instanceof IDiagramModelArchimateConnection) {
                        ((IDiagramModelArchimateConnection) dc)
                                .setArchimateRelationship(newRel);
                        reattached++;
                    }
                }

                target = newRel;
                entry.put("new_type", eClass.getName());
                entry.put("connections_preserved", reattached);
            }
        }

        if (item.has("name")) {
            target.setName(item.get("name").asText());
        }
        if (item.has("documentation")) {
            target.setDocumentation(item.get("documentation").asText());
        }
        RelationshipAttributes.apply(target, item);
        int removed = ModelAccessor.applyProperties(target, item);
        if (item.has("properties") || item.has("remove_properties")) {
            entry.put("removed", removed);
        }
        ModelAccessor.applySpecialization(model, target, item);
        if (item.has("specialization")) {
            dropped.remove("specialization");
        }
        if (!dropped.isEmpty()) {
            entry.put("dropped", dropped);
        }
        if (item.has("new_folder_id")) {
            String fid = item.get("new_folder_id").asText();
            IFolder nf = ModelAccessor.findFolderById(model, fid);
            if (nf == null) {
                throw new Exception("Folder not found: " + fid);
            }
            ModelAccessor.requireSection(model, nf, target);
            IFolder cur = (IFolder) target.eContainer();
            if (cur != null) {
                cur.getElements().remove(target);
            }
            nf.getElements().add(target);
            entry.put("folder_id", nf.getId());
        }

        entry.put("id", target.getId());
        if (warning != null) {
            entry.put("warning", warning);
        }
        return entry;
    }
}
