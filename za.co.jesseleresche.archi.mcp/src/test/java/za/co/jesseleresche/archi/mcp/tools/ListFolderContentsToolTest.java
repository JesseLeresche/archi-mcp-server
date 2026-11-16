package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class ListFolderContentsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ListFolderContentsTool tool = new ListFolderContentsTool();

    @Test
    void listsDirectChildrenOrRecursively() throws Exception {
        IFolder business = models.model.getFolder(FolderType.BUSINESS);
        IArchimateElement top = models.element("BusinessActor", "Top");
        IFolder sub = IArchimateFactory.eINSTANCE.createFolder();
        sub.setName("Sub");
        business.getFolders().add(sub);
        IArchimateElement nested = IArchimateFactory.eINSTANCE.createBusinessRole();
        nested.setName("Nested");
        sub.getElements().add(nested);

        JsonNode r = run(tool, "{'folder_id':'" + business.getId() + "'}");
        assertEquals(1, r.get("elements").size());
        assertEquals(top.getId(), r.get("elements").get(0).get("id").asText());
        assertEquals("BusinessActor", r.get("elements").get(0).get("type").asText());
        assertEquals("Sub", r.get("subfolders").get(0).get("name").asText());
        assertEquals(0, r.get("views").size());

        r = run(tool, "{'folder_id':'" + business.getId() + "','include_subfolders':true}");
        assertEquals(2, r.get("elements").size());
    }

    @Test
    void listsRelationshipsAndViews() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        models.relationship("AssignmentRelationship", a, b);
        models.view("V");
        JsonNode rels = run(tool, "{'folder_id':'" + models.model.getFolder(FolderType.RELATIONS).getId() + "'}")
                .get("relationships");
        assertEquals(a.getId(), rels.get(0).get("source_id").asText());
        assertEquals(b.getId(), rels.get(0).get("target_id").asText());
        JsonNode views = run(tool, "{'folder_id':'" + models.model.getFolder(FolderType.DIAGRAMS).getId() + "'}")
                .get("views");
        assertEquals("V", views.get(0).get("name").asText());
    }

    @Test
    void rejectsUnknownFolder() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'folder_id':'nope'}"));
        assertEquals("Folder not found: nope", ex.getMessage());
    }
}
