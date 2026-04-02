package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.DirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;

/**
 * Auto-layouts all figures on a view using Eclipse Draw2d's hierarchical
 * (Sugiyama-style) directed graph layout algorithm.
 * <p>
 * Handles nested containers bottom-up: children are laid out first,
 * then their parent container is resized to fit, then the parent level
 * is laid out.
 */
public class LayoutViewTool implements ITool {

    private static final int CONTAINER_PAD_LEFT = 15;
    private static final int CONTAINER_PAD_TOP = 30;
    private static final int CONTAINER_PAD_RIGHT = 15;
    private static final int CONTAINER_PAD_BOTTOM = 15;

    private int totalRepositioned = 0;

    @Override
    public String getName() {
        return "layout_view";
    }

    @Override
    public String getDescription() {
        return "Auto-layout all figures on a view using a hierarchical directed graph algorithm. "
                + "Recursively lays out nested elements inside containers, resizes containers "
                + "to fit their children, then lays out the top-level elements.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to layout");

        ObjectNode spacing = properties.putObject("spacing");
        spacing.put("type", "integer");
        spacing.put("description",
                "Spacing between elements in pixels (default 60)");

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
        int spacing = args.has("spacing") ? args.get("spacing").asInt(60) : 60;

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            totalRepositioned = 0;

            // Bottom-up: layout children inside each container first,
            // then layout the container's level
            layoutChildrenOf(view, spacing);

            // Clear all bendpoints (they're invalid after layout)
            for (IDiagramModelObject fig : ModelAccessor.collectAllDiagramObjects(view)) {
                for (var conn : fig.getSourceConnections()) {
                    if (conn instanceof IDiagramModelArchimateConnection c) {
                        c.getBendpoints().clear();
                    }
                }
            }

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("figures_repositioned", totalRepositioned);
            entry.put("algorithm", "hierarchical");
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    /**
     * Recursively layout children inside a container (view or figure).
     * Works bottom-up: lays out the deepest children first, then resizes
     * parent containers, then lays out the current level.
     */
    private void layoutChildrenOf(IDiagramModelContainer container, int spacing) {
        List<IDiagramModelObject> children = new ArrayList<>();
        for (IDiagramModelObject dmo : container.getChildren()) {
            children.add(dmo);
        }

        if (children.isEmpty()) return;

        // Step 1: Recurse into child containers first (bottom-up)
        for (IDiagramModelObject child : children) {
            if (child instanceof IDiagramModelContainer childContainer) {
                if (!childContainer.getChildren().isEmpty()) {
                    layoutChildrenOf(childContainer, spacing);
                    resizeContainerToFit(childContainer);
                }
            }
        }

        // Step 2: Build mapping from any descendant to its direct-child ancestor
        Map<String, IDiagramModelObject> descendantToDirectChild = new HashMap<>();
        for (IDiagramModelObject child : children) {
            descendantToDirectChild.put(child.getId(), child);
            if (child instanceof IDiagramModelContainer childContainer) {
                mapDescendants(childContainer, child, descendantToDirectChild);
            }
        }

        // Step 3: Build directed graph of direct children
        DirectedGraph graph = new DirectedGraph();
        graph.setDefaultPadding(new Insets(spacing / 2));

        Map<String, Node> nodeMap = new HashMap<>();
        for (IDiagramModelObject child : children) {
            IBounds bounds = child.getBounds();
            int w = bounds.getWidth() > 0 ? bounds.getWidth() : 120;
            int h = bounds.getHeight() > 0 ? bounds.getHeight() : 55;

            Node node = new Node(child);
            node.width = w;
            node.height = h;
            graph.nodes.add(node);
            nodeMap.put(child.getId(), node);
        }

        // Step 4: Build edges from connections between children
        List<IDiagramModelObject> allDescendants = new ArrayList<>();
        collectDescendants(container, allDescendants);

        for (IDiagramModelObject fig : allDescendants) {
            for (var conn : fig.getSourceConnections()) {
                if (conn.getTarget() == null) continue;

                IDiagramModelObject targetFig = (IDiagramModelObject) conn.getTarget();

                IDiagramModelObject sourceChild =
                        descendantToDirectChild.get(fig.getId());
                IDiagramModelObject targetChild =
                        descendantToDirectChild.get(targetFig.getId());

                if (sourceChild == null || targetChild == null) continue;
                if (sourceChild == targetChild) continue;

                Node sourceNode = nodeMap.get(sourceChild.getId());
                Node targetNode = nodeMap.get(targetChild.getId());

                if (sourceNode != null && targetNode != null) {
                    if (!edgeExists(graph, sourceNode, targetNode)) {
                        graph.edges.add(new Edge(sourceNode, targetNode));
                    }
                }
            }
        }

        // Step 5: Run layout
        if (graph.nodes.size() > 0) {
            boolean isViewLevel = container instanceof IArchimateDiagramModel;
            int offsetX = isViewLevel ? spacing : CONTAINER_PAD_LEFT;
            int offsetY = isViewLevel ? spacing : CONTAINER_PAD_TOP;

            try {
                new DirectedGraphLayout().visit(graph);

                for (Object obj : graph.nodes) {
                    Node node = (Node) obj;
                    IDiagramModelObject fig = (IDiagramModelObject) node.data;

                    IBounds newBounds = IArchimateFactory.eINSTANCE.createBounds();
                    newBounds.setX(node.x + offsetX);
                    newBounds.setY(node.y + offsetY);
                    newBounds.setWidth(node.width);
                    newBounds.setHeight(node.height);
                    fig.setBounds(newBounds);
                    totalRepositioned++;
                }
            } catch (Exception e) {
                applyGridLayout(children, spacing, offsetX, offsetY);
            }
        }
    }

    /**
     * Resize a container to fit all its children with padding.
     */
    private void resizeContainerToFit(IDiagramModelContainer container) {
        int maxRight = 0;
        int maxBottom = 0;

        for (IDiagramModelObject child : container.getChildren()) {
            IBounds b = child.getBounds();
            maxRight = Math.max(maxRight, b.getX() + b.getWidth());
            maxBottom = Math.max(maxBottom, b.getY() + b.getHeight());
        }

        if (maxRight > 0 && maxBottom > 0 && container instanceof IDiagramModelObject dmo) {
            IBounds current = dmo.getBounds();
            int newWidth = maxRight + CONTAINER_PAD_RIGHT;
            int newHeight = maxBottom + CONTAINER_PAD_BOTTOM;

            IBounds newBounds = IArchimateFactory.eINSTANCE.createBounds();
            newBounds.setX(current.getX());
            newBounds.setY(current.getY());
            newBounds.setWidth(newWidth);
            newBounds.setHeight(newHeight);
            dmo.setBounds(newBounds);
        }
    }

    /**
     * Map all descendants of a container to a direct child ancestor.
     */
    private void mapDescendants(IDiagramModelContainer container,
            IDiagramModelObject directChild,
            Map<String, IDiagramModelObject> map) {
        for (IDiagramModelObject dmo : container.getChildren()) {
            map.put(dmo.getId(), directChild);
            if (dmo instanceof IDiagramModelContainer nested) {
                mapDescendants(nested, directChild, map);
            }
        }
    }

    /**
     * Collect all descendant diagram objects from a container (not including
     * the container itself).
     */
    private void collectDescendants(IDiagramModelContainer container,
            List<IDiagramModelObject> results) {
        for (IDiagramModelObject child : container.getChildren()) {
            results.add(child);
            if (child instanceof IDiagramModelContainer nested) {
                collectDescendants(nested, results);
            }
        }
    }

    private boolean edgeExists(DirectedGraph graph, Node source, Node target) {
        for (Object e : graph.edges) {
            Edge existing = (Edge) e;
            if (existing.source == source && existing.target == target) {
                return true;
            }
        }
        return false;
    }

    private void applyGridLayout(List<IDiagramModelObject> figures,
            int spacing, int offsetX, int offsetY) {
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(figures.size())));
        int x = offsetX;
        int y = offsetY;
        int col = 0;
        int maxHeight = 0;

        for (IDiagramModelObject fig : figures) {
            IBounds bounds = fig.getBounds();
            int w = bounds.getWidth() > 0 ? bounds.getWidth() : 120;
            int h = bounds.getHeight() > 0 ? bounds.getHeight() : 55;

            IBounds newBounds = IArchimateFactory.eINSTANCE.createBounds();
            newBounds.setX(x);
            newBounds.setY(y);
            newBounds.setWidth(w);
            newBounds.setHeight(h);
            fig.setBounds(newBounds);
            totalRepositioned++;

            maxHeight = Math.max(maxHeight, h);
            x += w + spacing;
            col++;

            if (col >= cols) {
                col = 0;
                x = offsetX;
                y += maxHeight + spacing;
                maxHeight = 0;
            }
        }
    }
}
