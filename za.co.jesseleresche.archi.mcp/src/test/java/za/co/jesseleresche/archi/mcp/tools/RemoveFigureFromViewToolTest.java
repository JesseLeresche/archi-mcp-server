package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import org.eclipse.gef.commands.CommandStack;
import com.archimatetool.model.IDiagramModelGroup;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class RemoveFigureFromViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final RemoveFigureFromViewTool tool = new RemoveFigureFromViewTool();

    @Test
    void removesFigureAndItsConnectionsButKeepsModel() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        models.connection(fa, fb, rel);

        run(tool, "{'view_id':'" + v.getId() + "','figure_id':'" + fa.getId() + "'}");

        assertNull(ModelAccessor.findDiagramObjectById(v, fa.getId()));
        assertTrue(fb.getTargetConnections().isEmpty());
        assertNull(ModelAccessor.findConnectionByRelationshipId(v, rel.getId()));
        assertNotNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rel.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void removesNestedFigureByElementId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IArchimateElement a = models.element("BusinessActor", "A");
        models.figure(g, a, 10, 10, 120, 55);
        run(tool, "{'view_id':'" + v.getId() + "','element_id':'" + a.getId() + "'}");
        assertTrue(g.getChildren().isEmpty());
        assertSame(v, g.eContainer());
    }

    @Test
    void rejectsBadRequests() {
        IArchimateDiagramModel v = models.view("V");
        String vid = v.getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("Either figure_id or element_id"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','figure_id':'x'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','figure_id':'x'}"));
        assertTrue(e.getMessage().contains("Figure not found on view: x"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','element_id':'y'}"));
        assertTrue(e.getMessage().contains("No figure found for element on view: y"));
    }

    // --- figure removal leaves no dangling connections (spec figure-removal-dangling-connections)

    IArchimateDiagramModel v;
    IArchimateElement p, c, x;
    IDiagramModelArchimateObject fp, fc, fx;
    IArchimateRelationship rcx, rxc;
    IDiagramModelArchimateConnection kcx, kxc;

    /** View V: X at index 0, P at index 1 nesting C, another figure at index 2; C->X and X->C. */
    void nestedSetUp(boolean childIsSource, boolean childIsTarget) {
        v = models.view("V");
        p = models.element("ApplicationComponent", "P");
        c = models.element("ApplicationComponent", "C");
        x = models.element("ApplicationComponent", "X");
        fx = models.figure(v, x, 400, 0, 120, 55);
        fp = models.figure(v, p, 0, 0, 300, 200);
        models.figure(v, models.element("ApplicationComponent", "Other"), 400, 300, 120, 55);
        fc = models.figure(fp, c, 10, 30, 120, 55);
        if (childIsSource) {
            rcx = models.relationship("ServingRelationship", c, x);
            kcx = models.connection(fc, fx, rcx);
        }
        if (childIsTarget) {
            rxc = models.relationship("FlowRelationship", x, c);
            kxc = models.connection(fx, fc, rxc);
        }
    }

    /** S1.7 / FR-2, FR-3, FR-4. */
    @Test
    void removesSubtreeAndAllAttachedConnections() throws Exception {
        nestedSetUp(true, true);

        run(tool, "{'view_id':'" + v.getId() + "','figure_id':'" + fp.getId() + "'}");

        assertNull(ModelAccessor.findDiagramObjectById(v, fp.getId()));
        assertNull(ModelAccessor.findDiagramObjectById(v, fc.getId()));
        assertTrue(fx.getSourceConnections().isEmpty(), "X->C left on X");
        assertTrue(fx.getTargetConnections().isEmpty(), "C->X left on X");
        assertNotNull(ModelAccessor.findElementById(models.model, c.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rcx.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rxc.getId()));
        models.assertNoDanglingConnections();
    }

    /** S1.7 (X->C only) / FR-4, D3: EcoreUtil.delete leaves X->C on X with a null target. */
    @Test
    void noConnectionLeftWithNullEnd() throws Exception {
        nestedSetUp(false, true);

        run(tool, "{'view_id':'" + v.getId() + "','figure_id':'" + fp.getId() + "'}");

        for (IDiagramModelConnection k : fx.getSourceConnections()) {
            assertNotNull(k.getTarget(), "connection " + k.getId() + " has a null target");
        }
        assertTrue(fx.getSourceConnections().isEmpty());
        models.assertNoDanglingConnections();
    }

    /** S1.9 / FR-3 (D6). */
    @Test
    void connectionAttachedToRemovedConnectionIsRemoved() throws Exception {
        IArchimateDiagramModel view = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IArchimateElement ex = models.element("ApplicationComponent", "X");
        IArchimateRelationship rk = models.relationship("ServingRelationship", a, b);
        IArchimateRelationship rl = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        rl.connect(ex, rk);
        models.model.getDefaultFolderForObject(rl).getElements().add(rl);
        IDiagramModelArchimateObject fa = models.figure(view, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(view, b, 400, 0, 120, 55);
        IDiagramModelArchimateObject fex = models.figure(view, ex, 200, 200, 120, 55);
        IDiagramModelArchimateConnection k = models.connection(fa, fb, rk);
        IDiagramModelArchimateConnection l = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        l.setArchimateRelationship(rl);
        l.connect(fex, k);

        run(tool, "{'view_id':'" + view.getId() + "','figure_id':'" + fa.getId() + "'}");

        assertTrue(fb.getTargetConnections().isEmpty());
        assertTrue(fex.getSourceConnections().isEmpty(), "L (attached to removed K) left on X");
        models.assertNoDanglingConnections();
    }

    /** S1.8 / FR-6: one undo restores the figure at its parent/index and every connection, both ends. */
    @Test
    void undoRestoresFigureAndAllConnections() throws Exception {
        CommandStack stack = models.commandStack();
        nestedSetUp(true, true);

        run(tool, "{'view_id':'" + v.getId() + "','figure_id':'" + fp.getId() + "'}");
        stack.undo();

        assertSame(fp, v.getChildren().get(1));
        assertSame(fp, fc.eContainer());
        assertSame(fc, kcx.getSource());
        assertSame(fx, kcx.getTarget());
        assertTrue(fc.getSourceConnections().contains(kcx));
        assertTrue(fx.getTargetConnections().contains(kcx), "C->X not back in X's target connections");
        assertSame(fx, kxc.getSource());
        assertSame(fc, kxc.getTarget(), "X->C target not restored");
        assertTrue(fx.getSourceConnections().contains(kxc));
        assertTrue(fc.getTargetConnections().contains(kxc));
        models.assertNoDanglingConnections();
    }
}
