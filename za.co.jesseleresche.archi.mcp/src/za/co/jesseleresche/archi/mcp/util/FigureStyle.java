package za.co.jesseleresche.archi.mcp.util;

import java.util.function.Consumer;

import com.archimatetool.model.IFeatures;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared handling for the "absent = leave alone, null/empty = reset" JSON convention
 * used by colour and label-expression fields.
 */
public final class FigureStyle {

    /** Archi's native feature name for Label Expressions (shared with the Archi UI). */
    public static final String LABEL_EXPRESSION = "labelExpression";

    private FigureStyle() {
    }

    /**
     * Applies a colour field if present on {@code item}: JSON null or "" resets to the
     * Archi default (setter receives null); any other value is set as given.
     */
    public static void applyColor(JsonNode item, String key, Consumer<String> setter) {
        if (!item.has(key)) {
            return;
        }
        JsonNode n = item.get(key);
        setter.accept(n.isNull() || n.asText().isEmpty() ? null : n.asText());
    }

    /** Applies {@code label_expression} if present: null/"" clears, otherwise stored natively. */
    public static void applyLabelExpression(JsonNode item, IFeatures target) {
        if (!item.has("label_expression")) {
            return;
        }
        JsonNode n = item.get("label_expression");
        String v = n.isNull() ? "" : n.asText();
        // putString with a "" default removes the feature when value equals the default.
        target.getFeatures().putString(LABEL_EXPRESSION, v, "");
    }

    /** Current label expression, or null if none is set. */
    public static String getLabelExpression(IFeatures target) {
        String v = target.getFeatures().getString(LABEL_EXPRESSION, "");
        return v.isEmpty() ? null : v;
    }
}
