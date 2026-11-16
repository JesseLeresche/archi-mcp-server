package za.co.jesseleresche.archi.mcp.util;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModelNote;

class FigureStyleTest {

    @Test
    void applyColorLeavesAbsentAloneAndResetsNullOrEmpty() {
        List<String> seen = new ArrayList<>();
        FigureStyle.applyColor(json("{}"), "fill_color", seen::add);
        assertTrue(seen.isEmpty());

        FigureStyle.applyColor(json("{'fill_color':'#ff0000'}"), "fill_color", seen::add);
        FigureStyle.applyColor(json("{'fill_color':null}"), "fill_color", seen::add);
        FigureStyle.applyColor(json("{'fill_color':''}"), "fill_color", seen::add);
        assertEquals(java.util.Arrays.asList("#ff0000", null, null), seen);
    }

    @Test
    void labelExpressionSetAndClear() {
        IDiagramModelNote n = IArchimateFactory.eINSTANCE.createDiagramModelNote();
        assertNull(FigureStyle.getLabelExpression(n));

        FigureStyle.applyLabelExpression(json("{'label_expression':'${name}'}"), n);
        assertEquals("${name}", FigureStyle.getLabelExpression(n));
        assertEquals("${name}", n.getFeatures().getString(FigureStyle.LABEL_EXPRESSION, ""));

        FigureStyle.applyLabelExpression(json("{}"), n);
        assertEquals("${name}", FigureStyle.getLabelExpression(n));

        FigureStyle.applyLabelExpression(json("{'label_expression':null}"), n);
        assertNull(FigureStyle.getLabelExpression(n));
        assertTrue(n.getFeatures().isEmpty());
    }
}
