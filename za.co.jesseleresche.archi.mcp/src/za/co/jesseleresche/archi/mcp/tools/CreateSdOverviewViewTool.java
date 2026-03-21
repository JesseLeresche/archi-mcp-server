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
 * Composite tool that creates a complete BIAN SD Overview view with all
 * elements, relationships, figures, connections, and styling in one atomic
 * operation.
 */
public class CreateSdOverviewViewTool implements ITool {

    @Override
    public String getName() {
        return "create_sd_overview_view";
    }

    @Override
    public String getDescription() {
        return "Create a complete BIAN SD Overview view with all elements, "
                + "relationships, figures, connections, and styling. "
                + "Atomic — fully succeeds or rolls back.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        properties.putObject("sd_name").put("type", "string")
                .put("description", "Service Domain name (e.g. 'Document Directory')");
        properties.putObject("sd_element_id").put("type", "string")
                .put("description", "ID of existing focal BusinessService element");
        properties.putObject("view_folder_id").put("type", "string")
                .put("description", "Folder for the view");
        properties.putObject("element_folder_id").put("type", "string")
                .put("description", "Folder for new elements");
        properties.putObject("business_area").put("type", "string")
                .put("description", "For view documentation (e.g. 'Business Support')");
        properties.putObject("business_domain").put("type", "string")
                .put("description",
                        "For view documentation (e.g. 'Document Management and Archive')");
        properties.putObject("functional_pattern").put("type", "string")
                .put("description",
                        "FP name (e.g. 'Catalog', 'Manage', 'Fulfill')");
        properties.putObject("generic_artifact").put("type", "string")
                .put("description",
                        "GA name (e.g. 'Directory Entry', 'Management Plan')");
        properties.putObject("control_record").put("type", "string")
                .put("description",
                        "CR name (e.g. 'Document Directory Entry')");
        properties.putObject("asset_type").put("type", "string")
                .put("description",
                        "AT name (e.g. 'Document', 'Customer Relationship')");

        ObjectNode bqt = properties.putObject("behavior_qualifier_type");
        bqt.put("type", "string");
        bqt.put("description",
                "BQT name (e.g. 'Property', 'Duty', 'Feature'). Omit if none.");

        ObjectNode bqs = properties.putObject("behavior_qualifiers");
        bqs.put("type", "array");
        bqs.putObject("items").put("type", "string");
        bqs.put("description", "List of BQ names. Omit or empty if none.");

        ObjectNode ris = properties.putObject("reference_information");
        ris.put("type", "array");
        ris.putObject("items").put("type", "string");
        ris.put("description",
                "List of RI names. Omit or empty if none/NA.");

        ArrayNode required = schema.putArray("required");
        required.add("sd_name");
        required.add("sd_element_id");
        required.add("view_folder_id");
        required.add("element_folder_id");
        required.add("business_area");
        required.add("business_domain");
        required.add("functional_pattern");
        required.add("generic_artifact");
        required.add("control_record");
        required.add("asset_type");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        // Parse all parameters
        String sdName = args.get("sd_name").asText();
        String sdElementId = args.get("sd_element_id").asText();
        String viewFolderId = args.get("view_folder_id").asText();
        String elementFolderId = args.get("element_folder_id").asText();
        String businessArea = args.get("business_area").asText();
        String businessDomain = args.get("business_domain").asText();
        String fpName = args.get("functional_pattern").asText();
        String gaName = args.get("generic_artifact").asText();
        String crName = args.get("control_record").asText();
        String atName = args.get("asset_type").asText();
        String bqtName = args.has("behavior_qualifier_type")
                ? args.get("behavior_qualifier_type").asText() : null;

        List<String> bqNames = new ArrayList<>();
        if (args.has("behavior_qualifiers") && args.get("behavior_qualifiers").isArray()) {
            for (JsonNode bq : args.get("behavior_qualifiers")) {
                bqNames.add(bq.asText());
            }
        }

        List<String> riNames = new ArrayList<>();
        if (args.has("reference_information") && args.get("reference_information").isArray()) {
            for (JsonNode ri : args.get("reference_information")) {
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

        // Resolve EClasses
        EClass businessObjectClass = ModelAccessor.resolveElementClass("BusinessObject");
        EClass businessInteractionClass = ModelAccessor.resolveElementClass("BusinessInteraction");
        EClass businessServiceClass = ModelAccessor.resolveElementClass("BusinessService");
        EClass accessRelClass = ModelAccessor.resolveRelationshipClass("AccessRelationship");
        EClass realizationRelClass = ModelAccessor.resolveRelationshipClass("RealizationRelationship");
        EClass aggregationRelClass = ModelAccessor.resolveRelationshipClass("AggregationRelationship");

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
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

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("view_id", view.getId());
            entry.put("elements_created", elementsCreated);
            entry.put("relationships_created", relationshipsCreated);
            entry.put("figures_placed", figuresPlaced);
            entry.put("connections_drawn", connectionsDrawn);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
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
