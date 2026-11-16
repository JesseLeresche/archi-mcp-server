package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class BulkMoveElementsToFolderToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkMoveElementsToFolderTool tool = new BulkMoveElementsToFolderTool();

    IFolder sub(FolderType section) {
        IFolder f = IArchimateFactory.eINSTANCE.createFolder();
        models.model.getFolder(section).getFolders().add(f);
        return f;
    }

    @Test
    void movesElementsAndRelationships() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        IArchimateRelationship rel = models.relationship("AssignmentRelationship", a, b);
        IFolder bizSub = sub(FolderType.BUSINESS), relSub = sub(FolderType.RELATIONS);
        JsonNode r = run(tool, "{'moves':[{'folder_id':'" + bizSub.getId() + "','element_id':'" + a.getId() + "'},"
                + "{'folder_id':'" + relSub.getId() + "','relationship_id':'" + rel.getId() + "'}]}").get("results");
        assertSame(bizSub, a.eContainer());
        assertSame(relSub, rel.eContainer());
        assertEquals(models.model.getFolder(FolderType.BUSINESS).getId(), r.get(0).get("previous_folder_id").asText());
        assertEquals(a.getId(), r.get(0).get("element_id").asText());
        assertEquals(rel.getId(), r.get(1).get("relationship_id").asText());
        assertEquals(1, models.saves);
    }

    @Test
    void rejectsWrongSectionPerItem() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        IArchimateRelationship rel = models.relationship("AssignmentRelationship", a, b);
        IFolder app = sub(FolderType.APPLICATION), views = sub(FolderType.DIAGRAMS);
        JsonNode r = run(tool, "{'moves':[{'folder_id':'" + app.getId() + "','element_id':'" + a.getId() + "'},"
                + "{'folder_id':'" + views.getId() + "','relationship_id':'" + rel.getId() + "'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("must be placed under the 'Business' section"));
        assertTrue(r.get(1).get("error").asText().contains("relationships must be placed under the 'Relations' section"));
        assertSame(models.model.getFolder(FolderType.BUSINESS), a.eContainer());
        assertSame(models.model.getFolder(FolderType.RELATIONS), rel.eContainer());
    }

    @Test
    void reportsBadItemsAndEmptyMoves() throws Exception {
        IFolder biz = sub(FolderType.BUSINESS);
        JsonNode r = run(tool, "{'moves':[{'folder_id':'" + biz.getId() + "'},"
                + "{'folder_id':'nope','element_id':'x'},"
                + "{'folder_id':'" + biz.getId() + "','element_id':'ghost'},"
                + "{'folder_id':'" + biz.getId() + "','relationship_id':'ghost'}]}").get("results");
        assertEquals("Each item must specify either element_id or relationship_id", r.get(0).get("error").asText());
        assertEquals("Folder not found: nope", r.get(1).get("error").asText());
        assertEquals("Element not found: ghost", r.get(2).get("error").asText());
        assertEquals("Relationship not found: ghost", r.get(3).get("error").asText());
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'moves':[]}"));
        assertEquals("'moves' must be a non-empty array", ex.getMessage());
    }
}
