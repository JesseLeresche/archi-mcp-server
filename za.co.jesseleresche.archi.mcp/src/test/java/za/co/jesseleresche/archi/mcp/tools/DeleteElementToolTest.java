package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IFolder;
import org.eclipse.gef.commands.CommandStack;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class DeleteElementToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DeleteElementTool tool = new DeleteElementTool();

    IArchimateElement a, b;
    IArchimateRelationship rel;
    IArchimateDiagramModel view;
    IDiagramModelArchimateObject fa, fb;

    @BeforeEach
    void setUp() {
        a = models.element("BusinessActor", "A");
        b = models.element("BusinessRole", "B");
        rel = models.relationship("AssignmentRelationship", a, b);
        view = models.view("V");
        fa = models.figure(view, a, 0, 0, 120, 55);
        fb = models.figure(view, b, 200, 0, 120, 55);
        models.connection(fa, fb, rel);
    }

    @Test
    void dryRunReportsImpactWithoutChanges() throws Exception {
        JsonNode r = run(tool, "{'element_id':'" + a.getId() + "','dry_run':true}");
        assertEquals(1, r.get("relationships_to_delete").asInt());
        assertEquals(1, r.get("figures_to_remove").asInt());
        assertEquals(1, r.get("connections_to_remove").asInt());
        assertEquals(view.getId(), r.get("affected_view_ids").get(0).asText());
        assertNotNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNotNull(rel.eContainer());
        assertEquals(2, view.getChildren().size());
        assertEquals(0, models.saves);
    }

    @Test
    void deletesElementRelationshipsFiguresAndConnections() throws Exception {
        JsonNode r = run(tool, "{'element_id':'" + a.getId() + "'}");
        assertEquals(1, r.get("relationships_deleted").asInt());
        assertEquals(1, r.get("figures_removed").asInt());
        assertNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNull(rel.eContainer());
        assertEquals(1, view.getChildren().size());
        assertSame(fb, view.getChildren().get(0));
        assertTrue(fb.getTargetConnections().isEmpty());
        assertNotNull(ModelAccessor.findElementById(models.model, b.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void unknownElementFails() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'element_id':'ghost'}"));
        assertEquals("Element not found: ghost", ex.getMessage());
    }

    // --- figure removal leaves no dangling connections (spec figure-removal-dangling-connections)

    /** S1.1 / FR-2, FR-3, FR-4: a nested child's outgoing connection leaves with the parent. */
    @Test
    void nestedChildSourceConnectionIsRemoved() throws Exception {
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelArchimateObject fp = models.figure(v, p, 0, 0, 300, 200);
        IDiagramModelArchimateObject fc = models.figure(fp, c, 10, 30, 120, 55);
        IDiagramModelArchimateObject fx = models.figure(v, x, 400, 0, 120, 55);
        models.connection(fc, fx, models.relationship("ServingRelationship", c, x));

        run(tool, "{'element_id':'" + p.getId() + "'}");

        assertNull(ModelAccessor.findDiagramObjectById(v, fp.getId()));
        assertNull(ModelAccessor.findDiagramObjectById(v, fc.getId()));
        assertTrue(fx.getSourceConnections().isEmpty());
        assertTrue(fx.getTargetConnections().isEmpty(), "target-side ghost left on X");
        models.assertNoDanglingConnections();
    }

    /** S1.2 / FR-3, FR-4: a nested child's incoming connection is removed from the surviving source. */
    @Test
    void nestedChildTargetConnectionIsRemoved() throws Exception {
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelArchimateObject fp = models.figure(v, p, 0, 0, 300, 200);
        IDiagramModelArchimateObject fc = models.figure(fp, c, 10, 30, 120, 55);
        IDiagramModelArchimateObject fx = models.figure(v, x, 400, 0, 120, 55);
        models.connection(fx, fc, models.relationship("ServingRelationship", x, c));

        run(tool, "{'element_id':'" + p.getId() + "'}");

        assertTrue(fx.getSourceConnections().isEmpty(), "connection to detached C left on X");
        assertTrue(fx.getTargetConnections().isEmpty());
        models.assertNoDanglingConnections();
    }

    /** S1.3 / FR-3, FR-4: P > group G > C, with C connected to X outside P. */
    @Test
    void deeplyNestedChildConnectionIsRemoved() throws Exception {
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelArchimateObject fp = models.figure(v, p, 0, 0, 400, 300);
        IDiagramModelGroup g = models.group(fp, "G", 10, 30, 300, 200);
        IDiagramModelArchimateObject fc = models.figure(g, c, 10, 30, 120, 55);
        IDiagramModelArchimateObject fx = models.figure(v, x, 500, 0, 120, 55);
        models.connection(fc, fx, models.relationship("ServingRelationship", c, x));

        run(tool, "{'element_id':'" + p.getId() + "'}");

        assertTrue(fx.getTargetConnections().isEmpty());
        models.assertNoDanglingConnections();
    }

    /** S1.4 / FR-1, FR-7: an element shown twice on one view loses both figures. */
    @Test
    void removesEveryFigureIncludingSecondOnSameView() throws Exception {
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelArchimateObject f1 = models.figure(v, p, 0, 0, 120, 55);
        IDiagramModelArchimateObject f2 = models.figure(v, p, 0, 200, 120, 55);
        IDiagramModelArchimateObject fx = models.figure(v, x, 400, 0, 120, 55);
        models.connection(f1, fx, models.relationship("ServingRelationship", p, x));
        models.connection(f2, fx, models.relationship("FlowRelationship", p, x));

        JsonNode r = run(tool, "{'element_id':'" + p.getId() + "'}");

        assertNull(ModelAccessor.findDiagramObjectById(v, f1.getId()));
        assertNull(ModelAccessor.findDiagramObjectById(v, f2.getId()), "second figure on the same view left behind");
        assertTrue(fx.getSourceConnections().isEmpty());
        assertTrue(fx.getTargetConnections().isEmpty());
        assertEquals(2, r.get("figures_removed").asInt());
        models.assertNoDanglingConnections();
    }

    /** FR-1: a figure nested in a group is found and removed. */
    @Test
    void removesNestedFigureOfElement() throws Exception {
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IDiagramModelArchimateObject fp = models.figure(g, p, 10, 30, 120, 55);
        IDiagramModelArchimateObject fx = models.figure(v, x, 500, 0, 120, 55);
        models.connection(fp, fx, models.relationship("ServingRelationship", p, x));

        JsonNode r = run(tool, "{'element_id':'" + p.getId() + "'}");

        assertTrue(g.getChildren().isEmpty());
        assertTrue(fx.getTargetConnections().isEmpty());
        assertEquals(1, r.get("figures_removed").asInt());
        models.assertNoDanglingConnections();
    }

    /** S1.9 / FR-3 (D6): a connection attached to a removed connection is removed too. */
    @Test
    void connectionAttachedToRemovedConnectionIsRemoved() throws Exception {
        IArchimateElement ea = models.element("ApplicationComponent", "A2");
        IArchimateElement eb = models.element("ApplicationComponent", "B2");
        IArchimateElement ex = models.element("ApplicationComponent", "X");
        IArchimateRelationship rk = models.relationship("ServingRelationship", ea, eb);
        IArchimateRelationship rl = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        rl.connect(ex, rk);
        models.model.getDefaultFolderForObject(rl).getElements().add(rl);
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelArchimateObject fa2 = models.figure(v, ea, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb2 = models.figure(v, eb, 400, 0, 120, 55);
        IDiagramModelArchimateObject fx = models.figure(v, ex, 200, 200, 120, 55);
        IDiagramModelArchimateConnection k = models.connection(fa2, fb2, rk);
        IDiagramModelArchimateConnection l = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        l.setArchimateRelationship(rl);
        l.connect(fx, k);

        run(tool, "{'element_id':'" + ea.getId() + "'}");

        assertTrue(fb2.getTargetConnections().isEmpty());
        assertTrue(fx.getSourceConnections().isEmpty(), "connection L to removed connection K left on X");
        models.assertNoDanglingConnections();
    }

    /** S1.5 / FR-7: dry run counts figures (not views) and every distinct connection, descendants included. */
    @Test
    void dryRunCountsFiguresNotViews() throws Exception {
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateRelationship rpx = models.relationship("ServingRelationship", p, x);
        IArchimateDiagramModel v1 = models.view("V1");
        IDiagramModelArchimateObject fp = models.figure(v1, p, 0, 0, 300, 200);
        IDiagramModelArchimateObject fc = models.figure(fp, c, 10, 30, 120, 55);
        IDiagramModelArchimateObject fx1 = models.figure(v1, x, 400, 0, 120, 55);
        models.connection(fc, fx1, models.relationship("ServingRelationship", c, x));
        IArchimateDiagramModel v2 = models.view("V2");
        IDiagramModelArchimateObject f2a = models.figure(v2, p, 0, 0, 120, 55);
        IDiagramModelArchimateObject f2b = models.figure(v2, p, 0, 200, 120, 55);
        IDiagramModelArchimateObject fx2 = models.figure(v2, x, 400, 0, 120, 55);
        models.connection(f2a, fx2, rpx);
        models.connection(f2b, fx2, rpx);

        JsonNode r = run(tool, "{'element_id':'" + p.getId() + "','dry_run':true}");

        assertEquals(3, r.get("figures_to_remove").asInt());
        assertEquals(3, r.get("connections_to_remove").asInt());
        assertEquals(1, r.get("relationships_to_delete").asInt());
        JsonNode views = r.get("affected_view_ids");
        assertEquals(2, views.size(), views.toString());
        assertEquals(v1.getId(), views.get(0).asText());
        assertEquals(v2.getId(), views.get(1).asText());
        assertEquals(2, v1.getChildren().size());
        assertEquals(3, v2.getChildren().size());
        assertEquals(1, fx1.getTargetConnections().size());
        assertEquals(2, fx2.getTargetConnections().size());
        assertNotNull(ModelAccessor.findElementById(models.model, p.getId()));
        assertEquals(0, models.saves);
    }

    /** S1.6 / FR-5: one undo restores element, relationships, figures (same parent/index) and connections. */
    @Test
    void undoRestoresFiguresConnectionsRelationshipsAndElement() throws Exception {
        CommandStack stack = models.commandStack();
        IArchimateElement p = models.element("ApplicationComponent", "P");
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateRelationship rpx = models.relationship("ServingRelationship", p, x);
        IArchimateRelationship rcx = models.relationship("ServingRelationship", c, x);
        IArchimateRelationship rxc = models.relationship("FlowRelationship", x, c);
        IArchimateDiagramModel v = models.view("V2");
        IDiagramModelArchimateObject fx = models.figure(v, x, 400, 0, 120, 55);
        IDiagramModelArchimateObject fp = models.figure(v, p, 0, 0, 300, 200);
        models.figure(v, x, 400, 300, 120, 55);
        IDiagramModelArchimateObject fc = models.figure(fp, c, 10, 30, 120, 55);
        IDiagramModelArchimateConnection kpx = models.connection(fp, fx, rpx);
        IDiagramModelArchimateConnection kcx = models.connection(fc, fx, rcx);
        IDiagramModelArchimateConnection kxc = models.connection(fx, fc, rxc);
        IFolder elementFolder = (IFolder) p.eContainer();
        int elementIndex = elementFolder.getElements().indexOf(p);
        IFolder relFolder = (IFolder) rpx.eContainer();
        int relIndex = relFolder.getElements().indexOf(rpx);

        run(tool, "{'element_id':'" + p.getId() + "'}");
        assertTrue(fx.getSourceConnections().isEmpty() && fx.getTargetConnections().isEmpty(),
                "delete must remove every connection of the subtree before undo is meaningful");

        stack.undo();

        assertSame(p, elementFolder.getElements().get(elementIndex));
        assertSame(rpx, relFolder.getElements().get(relIndex));
        assertSame(fp, v.getChildren().get(1));
        assertSame(fp, fc.eContainer());
        for (IDiagramModelArchimateConnection k : java.util.List.of(kpx, kcx, kxc)) {
            assertTrue(k.getSource().getSourceConnections().contains(k), k.getId());
            assertTrue(k.getTarget().getTargetConnections().contains(k), k.getId());
        }
        assertSame(fc, kcx.getSource());
        assertSame(fc, kxc.getTarget());
        models.assertNoDanglingConnections();
    }
}
