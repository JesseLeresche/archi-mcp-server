package za.co.jesseleresche.archi.mcp.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IFolder;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class ModelAccessorTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    @Test
    void openModelDefaultsToFirstAndFollowsSelection() {
        IArchimateModel second = models.newModel("Second");
        assertSame(models.model, ModelAccessor.getOpenModel());
        assertEquals(2, ModelAccessor.getAllModels().size());

        assertSame(second, ModelAccessor.selectModel("SECOND"));
        assertEquals(second.getId(), ModelAccessor.getSelectedModelId());
        assertSame(second, ModelAccessor.getOpenModel());

        assertSame(models.model, ModelAccessor.selectModel(models.model.getId()));
        assertNull(ModelAccessor.selectModel("nope"));

        models.closeAll();
        assertNull(ModelAccessor.getOpenModel());
    }

    @Test
    void findsObjectsById() {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        IArchimateRelationship r = models.relationship("AssignmentRelationship", a, b);
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IDiagramModelArchimateObject fa = models.figure(g, a, 10, 10, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 500, 10, 120, 55);
        var c = models.connection(fa, fb, r);

        assertSame(a, ModelAccessor.findElementById(models.model, a.getId()));
        assertNull(ModelAccessor.findElementById(models.model, r.getId()));
        assertSame(r, ModelAccessor.findRelationshipById(models.model, r.getId()));
        assertSame(v, ModelAccessor.findViewById(models.model, v.getId()));
        assertSame(fa, ModelAccessor.findFigureById(v, fa.getId()));
        assertSame(fa, ModelAccessor.findFigureByElementId(v, a.getId()));
        assertSame(c, ModelAccessor.findConnectionByRelationshipId(v, r.getId()));
        assertSame(c, ModelAccessor.findConnectionById(v, c.getId()));
        assertEquals(2, ModelAccessor.collectAllFigures(v).size());
        assertEquals(3, ModelAccessor.collectAllDiagramObjects(v).size());
        assertEquals(1, ModelAccessor.getAllViews(models.model).size());
    }

    @Test
    void resolvesTypesCaseInsensitivelyByKind() {
        assertEquals(IArchimatePackage.eINSTANCE.getApplicationComponent(),
                ModelAccessor.resolveElementClass("applicationcomponent"));
        assertNull(ModelAccessor.resolveElementClass("AssignmentRelationship"));
        assertEquals(IArchimatePackage.eINSTANCE.getServingRelationship(),
                ModelAccessor.resolveRelationshipClass("servingrelationship"));
        assertNull(ModelAccessor.resolveRelationshipClass("BusinessActor"));
    }

    @Test
    void folderPathsCreateAndEnforceSections() throws Exception {
        IFolder f = ModelAccessor.findOrCreateFolderByPath(models.model, "application/Domains/CRM");
        assertEquals("Application/Domains/CRM", ModelAccessor.getFolderPath(f));
        assertSame(f, ModelAccessor.findOrCreateFolderByPath(models.model, "Application/Domains/CRM"));
        assertSame(f, ModelAccessor.findFolderById(models.model, f.getId()));
        assertNull(ModelAccessor.findOrCreateFolderByPath(models.model, "NoSuchRoot/x"));

        IArchimateElement comp = models.element("ApplicationComponent", "C");
        // non-section first segment is created under the object's own section
        IFolder sub = ModelAccessor.findOrCreateFolderByPath(models.model, "Data Flows", comp);
        assertEquals("Application/Data Flows", ModelAccessor.getFolderPath(sub));

        Exception e = assertThrows(Exception.class,
                () -> ModelAccessor.findOrCreateFolderByPath(models.model, "Business/X", comp));
        assertTrue(e.getMessage().contains("'Application' section"), e.getMessage());

        IFolder views = ModelAccessor.getViewsFolder(models.model);
        assertEquals("Views", views.getName());
        Exception e2 = assertThrows(Exception.class,
                () -> ModelAccessor.requireSection(models.model, views, comp));
        assertTrue(e2.getMessage().contains("under section 'Views'"), e2.getMessage());
        ModelAccessor.requireSection(models.model, f, comp);
    }

    @Test
    void saveModelGoesThroughSource() throws Exception {
        ModelAccessor.saveModel(models.model);
        assertEquals(1, models.saves);
    }

    /** FR-8: a connection whose source figure was detached (target-side ghost) is still found. */
    @Test
    void findsConnectionWhoseSourceIsDetached() {
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateElement t = models.element("ApplicationComponent", "T");
        IArchimateRelationship r = models.relationship("ServingRelationship", c, t);
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 300, 200);
        IDiagramModelArchimateObject fc = models.figure(g, c, 10, 30, 120, 55);
        IDiagramModelArchimateObject ft = models.figure(v, t, 400, 0, 120, 55);
        IDiagramModelArchimateConnection k = models.connection(fc, ft, r);
        v.getChildren().remove(g); // detach the source without disconnecting

        assertSame(k, ModelAccessor.findConnectionById(v, k.getId()));
        assertSame(k, ModelAccessor.findConnectionByRelationshipId(v, r.getId()));
    }

    /** FR-8: connectionsOnView is distinct and in tree order, then connections on connections. */
    @Test
    void connectionsOnViewIsDistinctAndOrdered() {
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IArchimateElement c = models.element("ApplicationComponent", "C");
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 200, 0, 120, 55);
        IDiagramModelArchimateObject fc = models.figure(v, c, 400, 0, 120, 55);
        IDiagramModelArchimateConnection k1 = models.connection(fa, fb, models.relationship("ServingRelationship", a, b));
        IDiagramModelArchimateConnection k2 = models.connection(fc, fa, models.relationship("ServingRelationship", c, a));
        IDiagramModelArchimateConnection k3 = models.connection(fb, fc, models.relationship("ServingRelationship", b, c));
        IDiagramModelConnection l = IArchimateFactory.eINSTANCE.createDiagramModelConnection();
        l.connect(k1, k3); // only reachable through connections

        // A: source K1, target K2; B: source K3 (target K1 already seen); C: nothing new; then L.
        assertEquals(java.util.List.of(k1, k2, k3, l), new java.util.ArrayList<>(ModelAccessor.connectionsOnView(v)));
    }
}
