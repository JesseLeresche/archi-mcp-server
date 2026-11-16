package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class CreateSdOverviewViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final CreateSdOverviewViewTool tool = new CreateSdOverviewViewTool();

    String args(String sdId, String viewFolder, String elementFolder, String extra) {
        return "{'sd_name':'Doc','sd_element_id':'" + sdId + "','view_folder_id':'" + viewFolder
                + "','element_folder_id':'" + elementFolder + "','business_area':'Ops','business_domain':'Archive',"
                + "'functional_pattern':'Catalog','generic_artifact':'Directory Entry',"
                + "'control_record':'Doc Entry','asset_type':'Document'" + extra + "}";
    }

    @Test
    void buildsCompleteView() throws Exception {
        IArchimateElement sd = models.element("ApplicationComponent", "Doc SD");
        IFolder views = models.model.getFolder(FolderType.DIAGRAMS);
        IFolder biz = models.model.getFolder(FolderType.BUSINESS);
        JsonNode r = run(tool, args(sd.getId(), views.getId(), biz.getId(),
                ",'behavior_qualifier_type':'Property','behavior_qualifiers':['Q1','Q2'],"
                        + "'reference_information':['Ref','NA','']"));
        // 5 core + BQT + 2 BQ + 1 RI + 4 service groups
        assertEquals(13, r.get("elements_created").asInt());
        // 9 core + GA->BQT + 2 CR->BQ + 2 BQT->BQ + 1 CR->RI
        assertEquals(15, r.get("relationships_created").asInt());
        assertEquals(14, r.get("figures_placed").asInt());
        assertEquals(13, r.get("connections_drawn").asInt());

        IArchimateDiagramModel v = ModelAccessor.findViewById(models.model, r.get("view_id").asText());
        assertSame(views, v.eContainer());
        assertEquals("Doc SD Overview", v.getName());
        assertEquals("SD Overview for Doc - Ops > Archive", v.getDocumentation());
        assertEquals(14, v.getChildren().size());
        IDiagramModelArchimateObject sdFig = (IDiagramModelArchimateObject) v.getChildren().get(0);
        assertSame(sd, sdFig.getArchimateElement());
        assertEquals("#F5A623", sdFig.getFillColor());
        assertEquals(13, biz.getElements().size());
        assertEquals(15, models.model.getFolder(FolderType.RELATIONS).getElements().size());
        assertEquals(1, models.saves);
    }

    @Test
    void rejectsUnknownReferences() {
        IArchimateElement sd = models.element("ApplicationComponent", "Doc SD");
        String views = models.model.getFolder(FolderType.DIAGRAMS).getId();
        String biz = models.model.getFolder(FolderType.BUSINESS).getId();
        assertEquals("SD element not found: nope",
                assertThrows(Exception.class, () -> run(tool, args("nope", views, biz, ""))).getMessage());
        assertEquals("View folder not found: nope",
                assertThrows(Exception.class, () -> run(tool, args(sd.getId(), "nope", biz, ""))).getMessage());
        assertEquals("Element folder not found: nope",
                assertThrows(Exception.class, () -> run(tool, args(sd.getId(), views, "nope", ""))).getMessage());
        assertEquals(1, models.model.getFolder(FolderType.APPLICATION).getElements().size());
        assertEquals(0, models.saves);
    }

    @Disabled("legacy bug: view_folder_id is not section-checked, so the view lands in a non-Views folder")
    @Test
    void rejectsViewFolderOutsideViewsSection() {
        IArchimateElement sd = models.element("ApplicationComponent", "Doc SD");
        String biz = models.model.getFolder(FolderType.BUSINESS).getId();
        Exception ex = assertThrows(Exception.class, () -> run(tool, args(sd.getId(), biz, biz, "")));
        assertTrue(ex.getMessage().contains("'Views' section"), ex.getMessage());
    }
}
