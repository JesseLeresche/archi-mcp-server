package za.co.jesseleresche.archi.mcp.util;

import java.util.function.Consumer;
import com.archimatetool.model.IFeatures;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared handling for the "absent = leave alone, null/empty = reset" JSON convention
 * used by colour and label-expression fields.
 */
public final class FigureStyle {

    /**
     * Archi's native feature name for Label Expressions (shared with the Archi UI).
     */
    public static final String LABEL_EXPRESSION = "labelExpression";

    private FigureStyle() {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Applies a colour field if present on {@code item}: JSON null or "" resets to the
     * Archi default (setter receives null); any other value is set as given.
     */
    public static void applyColor(JsonNode item, String key, Consumer<String> setter) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Applies {@code label_expression} if present: null/"" clears, otherwise stored natively.
     */
    public static void applyLabelExpression(JsonNode item, IFeatures target) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Current label expression, or null if none is set.
     */
    public static String getLabelExpression(IFeatures target) {
        throw new UnsupportedOperationException("stub");
    }
}
