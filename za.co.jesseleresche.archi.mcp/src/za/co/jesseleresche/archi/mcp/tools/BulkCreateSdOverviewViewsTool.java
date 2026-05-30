package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Bulk variant of {@link CreateSdOverviewViewTool}. Creates multiple BIAN SD
 * Overview views in a single call, sharing {@code view_folder_id} and
 * {@code element_folder_id} defaults across all items. Each item is built
 * independently with its own success/error result; a per-item failure does
 * not roll back successful items (mirrors {@code bulk_create_elements}).
 */
public class BulkCreateSdOverviewViewsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_create_sd_overview_views";
    }

    @Override
    public String getDescription() {
        return "Create multiple BIAN SD Overview views atomically in one call. "
                + "Per-item success/error result. view_folder_id and "
                + "element_folder_id are shared defaults that each item may "
                + "override. A failed item does not roll back successful items.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        properties.putObject("view_folder_id").put("type", "string")
                .put("description", "Shared default folder for all views");
        properties.putObject("element_folder_id").put("type", "string")
                .put("description", "Shared default folder for all new elements");

        ObjectNode views = properties.putObject("views");
        views.put("type", "array");
        views.put("description", "List of SD Overview views to create");
        ObjectNode items = views.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");

        itemProps.putObject("sd_name").put("type", "string")
                .put("description", "Service Domain name (e.g. 'Document Directory')");
        itemProps.putObject("sd_element_id").put("type", "string")
                .put("description", "ID of existing focal BusinessService element");
        itemProps.putObject("business_area").put("type", "string")
                .put("description", "For view documentation (e.g. 'Business Support')");
        itemProps.putObject("business_domain").put("type", "string")
                .put("description",
                        "For view documentation (e.g. 'Document Management and Archive')");
        itemProps.putObject("functional_pattern").put("type", "string")
                .put("description", "FP name (e.g. 'Catalog', 'Manage', 'Fulfill')");
        itemProps.putObject("generic_artifact").put("type", "string")
                .put("description", "GA name (e.g. 'Directory Entry', 'Management Plan')");
        itemProps.putObject("control_record").put("type", "string")
                .put("description", "CR name (e.g. 'Document Directory Entry')");
        itemProps.putObject("asset_type").put("type", "string")
                .put("description", "AT name (e.g. 'Document', 'Customer Relationship')");

        ObjectNode bqt = itemProps.putObject("behavior_qualifier_type");
        bqt.put("type", "string");
        bqt.put("description",
                "BQT name (e.g. 'Property', 'Duty', 'Feature'). Omit if none.");

        ObjectNode bqs = itemProps.putObject("behavior_qualifiers");
        bqs.put("type", "array");
        bqs.putObject("items").put("type", "string");
        bqs.put("description", "List of BQ names. Omit or empty if none.");

        ObjectNode ris = itemProps.putObject("reference_information");
        ris.put("type", "array");
        ris.putObject("items").put("type", "string");
        ris.put("description", "List of RI names. Omit or empty if none/NA.");

        itemProps.putObject("view_folder_id").put("type", "string")
                .put("description", "Override shared view folder for this item");
        itemProps.putObject("element_folder_id").put("type", "string")
                .put("description", "Override shared element folder for this item");

        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("sd_name");
        itemRequired.add("sd_element_id");
        itemRequired.add("business_area");
        itemRequired.add("business_domain");
        itemRequired.add("functional_pattern");
        itemRequired.add("generic_artifact");
        itemRequired.add("control_record");
        itemRequired.add("asset_type");

        ArrayNode required = schema.putArray("required");
        required.add("views");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode viewsNode = args.get("views");
        if (viewsNode == null || !viewsNode.isArray() || viewsNode.isEmpty()) {
            throw new Exception("'views' must be a non-empty array");
        }

        String sharedViewFolderId = args.has("view_folder_id")
                ? args.get("view_folder_id").asText() : null;
        String sharedElementFolderId = args.has("element_folder_id")
                ? args.get("element_folder_id").asText() : null;

        // Resolve EClasses once — type resolution is constant across the batch.
        EClass businessObjectClass = ModelAccessor.resolveElementClass("BusinessObject");
        EClass businessInteractionClass = ModelAccessor.resolveElementClass("BusinessInteraction");
        EClass businessServiceClass = ModelAccessor.resolveElementClass("BusinessService");
        EClass accessRelClass = ModelAccessor.resolveRelationshipClass("AccessRelationship");
        EClass realizationRelClass = ModelAccessor.resolveRelationshipClass("RealizationRelationship");
        EClass aggregationRelClass = ModelAccessor.resolveRelationshipClass("AggregationRelationship");

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : viewsNode) {
                String sdName = item.has("sd_name") ? item.get("sd_name").asText() : null;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("sd_name", sdName);
                try {
                    Map<String, Object> built = buildSdOverview(model, item,
                            sharedViewFolderId, sharedElementFolderId,
                            businessObjectClass, businessInteractionClass,
                            businessServiceClass, accessRelClass,
                            realizationRelClass, aggregationRelClass);
                    entry.put("ok", true);
                    entry.putAll(built);
                } catch (Exception e) {
                    entry.put("ok", false);
                    entry.put("error", e.getMessage() != null
                            ? e.getMessage() : e.getClass().getSimpleName());
                }
                entries.add(entry);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }

    /**
     * Builds a single SD Overview (elements, relationships, view, figures,
     * connections, styling) and returns its result counts. Throws on any
     * missing/invalid reference so the caller can record a per-item error.
     */
    private Map<String, Object> buildSdOverview(IArchimateModel model,
            JsonNode item, String sharedViewFolderId, String sharedElementFolderId,
            EClass businessObjectClass, EClass businessInteractionClass,
            EClass businessServiceClass, EClass accessRelClass,
            EClass realizationRelClass, EClass aggregationRelClass)
            throws Exception {

        // Parse parameters
        if (!item.has("sd_name") || item.get("sd_name").asText().isBlank()) {
            throw new Exception("Missing required field: sd_name");
        }
        String sdName = item.get("sd_name").asText();
        String sdElementId = requireText(item, "sd_element_id");
        String businessArea = requireText(item, "business_area");
        String businessDomain = requireText(item, "business_domain");
        String fpName = requireText(item, "functional_pattern");
        String gaName = requireText(item, "generic_artifact");
        String crName = requireText(item, "control_record");
        String atName = requireText(item, "asset_type");
        String bqtName = item.has("behavior_qualifier_type")
                ? item.get("behavior_qualifier_type").asText() : null;

        String viewFolderId = item.has("view_folder_id")
                ? item.get("view_folder_id").asText() : sharedViewFolderId;
        String elementFolderId = item.has("element_folder_id")
                ? item.get("element_folder_id").asText() : sharedElementFolderId;
        if (viewFolderId == null) {
            throw new Exception("No view_folder_id (shared or per-item)");
        }
        if (elementFolderId == null) {
            throw new Exception("No element_folder_id (shared or per-item)");
        }

        List<String> bqNames = new ArrayList<>();
        if (item.has("behavior_qualifiers") && item.get("behavior_qualifiers").isArray()) {
            for (JsonNode bq : item.get("behavior_qualifiers")) {
                bqNames.add(bq.asText());
            }
        }

        List<String> riNames = new ArrayList<>();
        if (item.has("reference_information") && item.get("reference_information").isArray()) {
            for (JsonNode ri : item.get("reference_information")) {
                String name = ri.asText();
                if (!"NA".equalsIgnoreCase(name) && !name.isBlank()) {
                    riNames.add(name);
                }
            }
        }

        // Validate references
        IArchimateElement sdElement = ModelAccessor.findElementById(model, sdElementId);
        if (sdElement == null) {
            throw new Exception("SD element not found: " + sdElementId);
        }

        IFolder viewFolder = ModelAccessor.findFolderById(model, viewFolderId);
        if (viewFolder == null) {
            throw new Exception("View folder not found: " + viewFolderId);
        }

        IFolder elementFolder = ModelAccessor.findFolderById(model, elementFolderId);
        if (elementFolder == null) {
            throw new Exception("Element folder not found: " + elementFolderId);
        }

        int elementsCreated = 0;
        int relationshipsCreated = 0;
        int figuresPlaced = 0;
        int connectionsDrawn = 0;

        // === 1. Create elements ===
        IArchimateElement analyticsObject = createElement(
                businessObjectClass, crName + " Analytics Object", elementFolder);
        elementsCreated++;

        IArchimateElement assetType = createElement(
                businessObjectClass, atName, elementFolder);
        elementsCreated++;

        IArchimateElement controlRecord = createElement(
                businessObjectClass, crName, elementFolder);
        elementsCreated++;

        IArchimateElement functionalPattern = createElement(
                businessInteractionClass, fpName, elementFolder);
        elementsCreated++;

        IArchimateElement genericArtifact = createElement(
                businessObjectClass, gaName, elementFolder);
        elementsCreated++;

        IArchimateElement bqtElement = null;
        if (bqtName != null && !bqtName.isBlank()) {
            bqtElement = createElement(
                    businessObjectClass, bqtName, elementFolder);
            elementsCreated++;
        }

        List<IArchimateElement> bqElements = new ArrayList<>();
        for (String bq : bqNames) {
            bqElements.add(createElement(
                    businessObjectClass, bq, elementFolder));
            elementsCreated++;
        }

        List<IArchimateElement> riElements = new ArrayList<>();
        for (String ri : riNames) {
            riElements.add(createElement(
                    businessObjectClass, ri, elementFolder));
            elementsCreated++;
        }

        IArchimateElement sgOperations = createElement(
                businessServiceClass, sdName + "_SD_Operations", elementFolder);
        elementsCreated++;
        IArchimateElement sgInstantiation = createElement(
                businessServiceClass, crName + "_Instantiation", elementFolder);
        elementsCreated++;
        IArchimateElement sgInvocation = createElement(
                businessServiceClass, crName + "_Invocation", elementFolder);
        elementsCreated++;
        IArchimateElement sgReporting = createElement(
                businessServiceClass, crName + "_Reporting", elementFolder);
        elementsCreated++;

        // === 2. Create relationships ===
        IFolder relFolder = model.getDefaultFolderForObject(
                IArchimateFactory.eINSTANCE.create(accessRelClass));

        // FP → GA (Access, Read/Write)
        IArchimateRelationship fpToGa = createRelationship(
                accessRelClass, functionalPattern, genericArtifact, relFolder);
        ((IAccessRelationship) fpToGa).setAccessType(3);
        relationshipsCreated++;

        // FP → SD (Realization)
        IArchimateRelationship fpToSd = createRelationship(
                realizationRelClass, functionalPattern, sdElement, relFolder);
        relationshipsCreated++;

        // AT → SD (Realization)
        IArchimateRelationship atToSd = createRelationship(
                realizationRelClass, assetType, sdElement, relFolder);
        relationshipsCreated++;

        // CR → SD (Realization)
        IArchimateRelationship crToSd = createRelationship(
                realizationRelClass, controlRecord, sdElement, relFolder);
        relationshipsCreated++;

        // AO → SD (Realization)
        IArchimateRelationship aoToSd = createRelationship(
                realizationRelClass, analyticsObject, sdElement, relFolder);
        relationshipsCreated++;

        // ServiceGroups → SD (Realization)
        IArchimateRelationship sg1ToSd = createRelationship(
                realizationRelClass, sgOperations, sdElement, relFolder);
        relationshipsCreated++;
        IArchimateRelationship sg2ToSd = createRelationship(
                realizationRelClass, sgInstantiation, sdElement, relFolder);
        relationshipsCreated++;
        IArchimateRelationship sg3ToSd = createRelationship(
                realizationRelClass, sgInvocation, sdElement, relFolder);
        relationshipsCreated++;
        IArchimateRelationship sg4ToSd = createRelationship(
                realizationRelClass, sgReporting, sdElement, relFolder);
        relationshipsCreated++;

        // GA → BQT (Aggregation) — if BQT exists
        IArchimateRelationship gaToBqt = null;
        if (bqtElement != null) {
            gaToBqt = createRelationship(
                    aggregationRelClass, genericArtifact, bqtElement, relFolder);
            relationshipsCreated++;
        }

        // CR → each BQ (Aggregation)
        List<IArchimateRelationship> crToBqRels = new ArrayList<>();
        for (IArchimateElement bq : bqElements) {
            crToBqRels.add(createRelationship(
                    aggregationRelClass, controlRecord, bq, relFolder));
            relationshipsCreated++;
        }

        // BQT → each BQ (Aggregation) — model only, NOT drawn on view
        if (bqtElement != null) {
            for (IArchimateElement bq : bqElements) {
                createRelationship(
                        aggregationRelClass, bqtElement, bq, relFolder);
                relationshipsCreated++;
            }
        }

        // CR → each RI (Aggregation)
        List<IArchimateRelationship> crToRiRels = new ArrayList<>();
        for (IArchimateElement ri : riElements) {
            crToRiRels.add(createRelationship(
                    aggregationRelClass, controlRecord, ri, relFolder));
            relationshipsCreated++;
        }

        // === 3. Create view ===
        IArchimateDiagramModel view =
                IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        view.setName(sdName + " SD Overview");
        view.setDocumentation("SD Overview for " + sdName
                + " - " + businessArea + " > " + businessDomain);
        viewFolder.getElements().add(view);

        // === 4. Place figures ===
        IDiagramModelArchimateObject sdFig = placeFigure(
                view, sdElement, 380, 320, 180, 60);
        figuresPlaced++;

        IDiagramModelArchimateObject fpFig = placeFigure(
                view, functionalPattern, 380, 144, 180, 55);
        figuresPlaced++;

        IDiagramModelArchimateObject gaFig = placeFigure(
                view, genericArtifact, 620, 144, 180, 55);
        figuresPlaced++;

        IDiagramModelArchimateObject bqtFig = null;
        if (bqtElement != null) {
            bqtFig = placeFigure(view, bqtElement, 876, 144, 200, 55);
            figuresPlaced++;
        }

        IDiagramModelArchimateObject aoFig = placeFigure(
                view, analyticsObject, 50, 252, 227, 55);
        figuresPlaced++;

        IDiagramModelArchimateObject atFig = placeFigure(
                view, assetType, 50, 334, 227, 41);
        figuresPlaced++;

        IDiagramModelArchimateObject crFig = placeFigure(
                view, controlRecord, 620, 320, 210, 55);
        figuresPlaced++;

        IDiagramModelArchimateObject sg1Fig = placeFigure(
                view, sgOperations, 96, 437, 227, 41);
        figuresPlaced++;
        IDiagramModelArchimateObject sg2Fig = placeFigure(
                view, sgInstantiation, 96, 497, 227, 45);
        figuresPlaced++;
        IDiagramModelArchimateObject sg3Fig = placeFigure(
                view, sgInvocation, 96, 557, 227, 40);
        figuresPlaced++;
        IDiagramModelArchimateObject sg4Fig = placeFigure(
                view, sgReporting, 96, 617, 227, 41);
        figuresPlaced++;

        List<IDiagramModelArchimateObject> bqFigs = new ArrayList<>();
        for (int i = 0; i < bqElements.size(); i++) {
            int bqY = 408 + i * 55;
            IDiagramModelArchimateObject bqFig = placeFigure(
                    view, bqElements.get(i), 768, bqY, 250, 45);
            bqFigs.add(bqFig);
            figuresPlaced++;
        }

        List<IDiagramModelArchimateObject> riFigs = new ArrayList<>();
        for (int i = 0; i < riElements.size(); i++) {
            IDiagramModelArchimateObject riFig = placeFigure(
                    view, riElements.get(i), 50, 700 + i * 65, 227, 55);
            riFigs.add(riFig);
            figuresPlaced++;
        }

        // === 5. Draw connections ===
        // Straight connections (no bendpoints)
        drawConnection(fpFig, gaFig, fpToGa);
        connectionsDrawn++;

        drawConnection(fpFig, sdFig, fpToSd);
        connectionsDrawn++;

        if (gaToBqt != null && bqtFig != null) {
            drawConnection(gaFig, bqtFig, gaToBqt);
            connectionsDrawn++;
        }

        drawConnection(atFig, sdFig, atToSd);
        connectionsDrawn++;

        drawConnection(crFig, sdFig, crToSd);
        connectionsDrawn++;

        // AO → SD with bendpoint
        drawConnection(aoFig, sdFig, aoToSd, 258, 16, -50, -74);
        connectionsDrawn++;

        // ServiceGroups → SD with bendpoints
        drawConnection(sg1Fig, sdFig, sg1ToSd, 271, -1, 10, 106);
        connectionsDrawn++;
        drawConnection(sg2Fig, sdFig, sg2ToSd, 271, -3, 10, 166);
        connectionsDrawn++;
        drawConnection(sg3Fig, sdFig, sg3ToSd, 271, -1, 10, 226);
        connectionsDrawn++;
        drawConnection(sg4Fig, sdFig, sg4ToSd, 271, -1, 10, 286);
        connectionsDrawn++;

        // CR → BQ with bendpoints
        for (int i = 0; i < bqElements.size(); i++) {
            int bqY = 408 + i * 55;
            int startY = (int) ((bqY + 22.5) - 347.5);
            drawConnection(crFig, bqFigs.get(i), crToBqRels.get(i),
                    -5, startY, -173, 0);
            connectionsDrawn++;
        }

        // CR → RI with bendpoints (route down-left)
        for (int i = 0; i < riElements.size(); i++) {
            int riY = 700 + i * 65;
            int startY = (int) ((riY + 27.5) - 347.5);
            drawConnection(crFig, riFigs.get(i), crToRiRels.get(i),
                    -5, startY, -173, 0);
            connectionsDrawn++;
        }

        // === 6. Style the SD figure ===
        sdFig.setFillColor("#F5A623");
        sdFig.setLineColor("#D4850F");

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("view_id", view.getId());
        entry.put("elements_created", elementsCreated);
        entry.put("relationships_created", relationshipsCreated);
        entry.put("figures_placed", figuresPlaced);
        entry.put("connections_drawn", connectionsDrawn);
        return entry;
    }

    private static String requireText(JsonNode item, String field) throws Exception {
        if (!item.has(field) || item.get(field).asText().isBlank()) {
            throw new Exception("Missing required field: " + field);
        }
        return item.get(field).asText();
    }

    private IArchimateElement createElement(EClass eClass, String name,
            IFolder folder) {
        EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
        IArchimateElement element = (IArchimateElement) eObject;
        element.setName(name);
        folder.getElements().add(element);
        return element;
    }

    private IArchimateRelationship createRelationship(EClass eClass,
            IArchimateElement source, IArchimateElement target,
            IFolder folder) {
        EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
        IArchimateRelationship rel = (IArchimateRelationship) eObject;
        rel.setSource(source);
        rel.setTarget(target);
        folder.getElements().add(rel);
        return rel;
    }

    private IDiagramModelArchimateObject placeFigure(
            IArchimateDiagramModel view, IArchimateElement element,
            int x, int y, int width, int height) {
        IDiagramModelArchimateObject figure =
                IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        figure.setArchimateElement(element);
        IBounds bounds = IArchimateFactory.eINSTANCE.createBounds();
        bounds.setX(x);
        bounds.setY(y);
        bounds.setWidth(width);
        bounds.setHeight(height);
        figure.setBounds(bounds);
        view.getChildren().add(figure);
        return figure;
    }

    private void drawConnection(IDiagramModelArchimateObject source,
            IDiagramModelArchimateObject target,
            IArchimateRelationship relationship) {
        IDiagramModelArchimateConnection conn =
                IArchimateFactory.eINSTANCE
                        .createDiagramModelArchimateConnection();
        conn.setArchimateRelationship(relationship);
        conn.connect(source, target);
    }

    private void drawConnection(IDiagramModelArchimateObject source,
            IDiagramModelArchimateObject target,
            IArchimateRelationship relationship,
            int startX, int startY, int endX, int endY) {
        IDiagramModelArchimateConnection conn =
                IArchimateFactory.eINSTANCE
                        .createDiagramModelArchimateConnection();
        conn.setArchimateRelationship(relationship);
        conn.connect(source, target);
        IDiagramModelBendpoint bp =
                IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(startX);
        bp.setStartY(startY);
        bp.setEndX(endX);
        bp.setEndY(endY);
        conn.getBendpoints().add(bp);
    }
}
