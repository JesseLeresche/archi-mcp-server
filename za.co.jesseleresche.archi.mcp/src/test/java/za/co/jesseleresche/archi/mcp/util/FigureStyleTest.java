package za.co.jesseleresche.archi.mcp.util;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelNote;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class FigureStyleTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    /** Two candidate values per field; the test uses whichever differs from the default. */
    static final Map<String, List<Object>> SAMPLES = Map.ofEntries(
            Map.entry("fill_color", List.of("#112233", "#445566")),
            Map.entry("line_color", List.of("#223344", "#556677")),
            Map.entry("font_color", List.of("#334455", "#667788")),
            Map.entry("font", List.of("1|Arial|12.0|1|COCOA|1|Arial-Bold", "1|Arial|9.0|0|COCOA|1|Arial")),
            Map.entry("opacity", List.of(100, 200)),
            Map.entry("line_opacity", List.of(100, 200)),
            Map.entry("line_width", List.of(3, 2)),
            Map.entry("line_style", List.of(2, 3)),
            Map.entry("text_alignment", List.of(4, 1)),
            Map.entry("text_position", List.of(2, 0)),
            Map.entry("gradient", List.of(1, 2)),
            Map.entry("derive_line_color", List.of(false, true)),
            Map.entry("icon_visible", List.of(2, 1)),
            Map.entry("icon_color", List.of("#778899", "#99aabb")),
            Map.entry("figure_type", List.of(1, 0)),
            Map.entry("border_type", List.of(1, 0)),
            Map.entry("image_source", List.of(1, 0)),
            Map.entry("image_path", List.of("images/a.png", "images/a.png")),
            Map.entry("image_position", List.of(9, 8)),
            Map.entry("hide_junction_arrows", List.of(true, false)),
            Map.entry("name_visible", List.of(false, true)),
            Map.entry("label_expression", List.of("${name}", "${type}")));

    static final Set<String> CONNECTION_FIELDS = Set.of("line_color", "font_color", "font", "line_width",
            "text_alignment", "text_position", "name_visible", "label_expression");
    static final Set<String> NOT_ON_GROUP_OR_NOTE = Set.of("figure_type", "image_source",
            "hide_junction_arrows", "name_visible");

    @Test
    void vocabularyCoversSamples() {
        assertEquals(SAMPLES.keySet(), Set.copyOf(FigureStyle.KEYS));
    }

    @Test
    void roundTripsEveryFieldOnEveryTargetType() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 200, 0, 120, 55);
        fb.setImagePath("images/a.png"); // makes the image known to the model
        Map<String, EObject> targets = Map.of(
                "element", fa,
                "group", models.group(v, "G", 0, 100, 300, 200),
                "note", models.note(v, "N", 400, 0, 100, 50),
                "connection", models.connection(fa, fb, models.relationship("ServingRelationship", a, b)));

        for (var t : targets.entrySet()) {
            for (String key : FigureStyle.KEYS) {
                boolean applies = switch (t.getKey()) {
                    case "connection" -> CONNECTION_FIELDS.contains(key);
                    case "element" -> !key.equals("border_type") && !key.equals("name_visible");
                    default -> !NOT_ON_GROUP_OR_NOTE.contains(key);
                };
                String where = t.getKey() + "." + key;
                EObject target = t.getValue();
                Map<String, Object> before = FigureStyle.read(target);
                if (!applies) {
                    ObjectNode item = item(key, SAMPLES.get(key).get(0));
                    Exception e = assertThrows(Exception.class, () -> FigureStyle.apply(item, target), where);
                    assertTrue(e.getMessage().contains("does not apply"), where);
                    assertEquals(before, FigureStyle.read(target), where);
                    continue;
                }
                Object value = SAMPLES.get(key).get(0);
                FigureStyle.apply(item(key, value), target);
                Map<String, Object> after = FigureStyle.read(target);
                if (!value.equals(after.get(key))) {
                    // the first candidate was the default; the second must stick
                    value = SAMPLES.get(key).get(1);
                    FigureStyle.apply(item(key, value), target);
                    after = FigureStyle.read(target);
                }
                assertEquals(value, after.get(key), where);

                // replaying read() output onto a fresh copy reproduces the style
                EObject copy = org.eclipse.emf.ecore.util.EcoreUtil.create(target.eClass());
                FigureStyle.copy(target, copy);
                assertEquals(FigureStyle.read(target), FigureStyle.read(copy), where);

                // null resets to the type default (absent from read())
                ObjectNode reset = json("{}").deepCopy();
                reset.putNull(key);
                FigureStyle.apply(reset, target);
                assertFalse(FigureStyle.read(target).containsKey(key), where);
            }
        }
    }

    @Test
    void emptyStringResetsAndAbsentLeavesAlone() throws Exception {
        IDiagramModelNote n = models.note(models.view("V"), "N", 0, 0, 10, 10);
        FigureStyle.apply(json("{'fill_color':'#ff0000','label_expression':'${name}'}"), n);
        FigureStyle.apply(json("{}"), n);
        assertEquals("#ff0000", n.getFillColor());
        assertEquals("${name}", FigureStyle.getLabelExpression(n));
        FigureStyle.apply(json("{'fill_color':'','label_expression':''}"), n);
        assertNull(n.getFillColor());
        assertNull(FigureStyle.getLabelExpression(n));
        assertTrue(n.getFeatures().isEmpty());
    }

    @Test
    void badValueLeavesTargetUnchanged() {
        IDiagramModelNote n = models.note(models.view("V"), "N", 0, 0, 10, 10);
        Exception e = assertThrows(Exception.class,
                () -> FigureStyle.apply(json("{'fill_color':'#ff0000','opacity':'lots'}"), n));
        assertTrue(e.getMessage().contains("'opacity' must be an integer"));
        assertNull(n.getFillColor());
        e = assertThrows(Exception.class, () -> FigureStyle.apply(json("{'gradient':true}"), n));
        assertTrue(e.getMessage().contains("'gradient' must be an integer"));
        e = assertThrows(Exception.class, () -> FigureStyle.apply(json("{'derive_line_color':1}"), n));
        assertTrue(e.getMessage().contains("must be true or false"));
    }

    @Test
    void imagePathMustAlreadyBeInModel() {
        IDiagramModelNote n = models.note(models.view("V"), "N", 0, 0, 10, 10);
        Exception e = assertThrows(Exception.class,
                () -> FigureStyle.apply(json("{'image_path':'images/nope.png'}"), n));
        assertTrue(e.getMessage().contains("not an image in this model"));
    }

    @Test
    void acceptsNumericStrings() throws Exception {
        IDiagramModelNote n = models.note(models.view("V"), "N", 0, 0, 10, 10);
        FigureStyle.apply(json("{'opacity':'120'}"), n);
        assertEquals(120, n.getAlpha());
    }

    private static ObjectNode item(String key, Object value) {
        ObjectNode o = json("{}").deepCopy();
        if (value instanceof Integer i) {
            o.put(key, i);
        } else if (value instanceof Boolean b) {
            o.put(key, b);
        } else {
            o.put(key, (String) value);
        }
        return o;
    }
}
