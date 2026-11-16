package za.co.jesseleresche.archi.mcp.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBorderType;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelImageProvider;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFeatures;
import com.archimatetool.model.IFontAttribute;
import com.archimatetool.model.IIconic;
import com.archimatetool.model.ILineObject;
import com.archimatetool.model.ITextAlignment;
import com.archimatetool.model.ITextPosition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The one style vocabulary shared by every tool that styles a figure or connection
 * ({@code set_style}, {@code add_figure}, {@code add_connection}, {@code update_connection},
 * duplicate, and the {@code definition} read-back).
 *
 * <p>JSON convention: an absent field is left unchanged; {@code null} or {@code ""} resets it
 * to the default Archi gives a new object of the same type; any other value is set. A field
 * that doesn't apply to the target (e.g. {@code border_type} on an element figure) is an error.
 * Values are Archi's native ones (colours "#rrggbb", font as Archi's font string, enums as ints).
 */
public final class FigureStyle {

    /** Archi's native feature name for Label Expressions (shared with the Archi UI). */
    public static final String LABEL_EXPRESSION = "labelExpression";

    private enum Kind { STRING, INT, BOOL }

    private record Field(String key, Predicate<Object> appliesTo, Kind kind,
            Function<Object, Object> get, BiConsumer<Object, Object> set) {
    }

    @SuppressWarnings("unchecked")
    private static <T> Field field(String key, Class<T> on, Kind kind, Function<T, Object> get,
            BiConsumer<T, Object> set) {
        return new Field(key, on::isInstance, kind, o -> get.apply((T) o), (o, v) -> set.accept((T) o, v));
    }

    private static final String HIDE_JUNCTION_ARROWS = IDiagramModelArchimateObject.FEATURE_HIDE_JUNCTION_ARROWS;

    /** Every supported style field, in output order. */
    private static final List<Field> FIELDS = List.of(
            field("fill_color", IDiagramModelObject.class, Kind.STRING,
                    IDiagramModelObject::getFillColor, (t, v) -> t.setFillColor((String) v)),
            field("line_color", ILineObject.class, Kind.STRING,
                    ILineObject::getLineColor, (t, v) -> t.setLineColor((String) v)),
            field("font_color", IFontAttribute.class, Kind.STRING,
                    IFontAttribute::getFontColor, (t, v) -> t.setFontColor((String) v)),
            field("font", IFontAttribute.class, Kind.STRING,
                    IFontAttribute::getFont, (t, v) -> t.setFont((String) v)),
            field("opacity", IDiagramModelObject.class, Kind.INT,
                    IDiagramModelObject::getAlpha, (t, v) -> t.setAlpha((Integer) v)),
            field("line_opacity", IDiagramModelObject.class, Kind.INT,
                    IDiagramModelObject::getLineAlpha, (t, v) -> t.setLineAlpha((Integer) v)),
            field("line_width", ILineObject.class, Kind.INT,
                    ILineObject::getLineWidth, (t, v) -> t.setLineWidth((Integer) v)),
            field("line_style", IDiagramModelObject.class, Kind.INT,
                    IDiagramModelObject::getLineStyle, (t, v) -> t.setLineStyle((Integer) v)),
            field("text_alignment", ITextAlignment.class, Kind.INT,
                    ITextAlignment::getTextAlignment, (t, v) -> t.setTextAlignment((Integer) v)),
            // Figures implement ITextPosition; connections have their own text position.
            new Field("text_position", t -> t instanceof ITextPosition || t instanceof IDiagramModelConnection,
                    Kind.INT,
                    t -> t instanceof ITextPosition p ? p.getTextPosition()
                            : ((IDiagramModelConnection) t).getTextPosition(),
                    (t, v) -> {
                        if (t instanceof ITextPosition p) {
                            p.setTextPosition((Integer) v);
                        } else {
                            ((IDiagramModelConnection) t).setTextPosition((Integer) v);
                        }
                    }),
            field("gradient", IDiagramModelObject.class, Kind.INT,
                    IDiagramModelObject::getGradient, (t, v) -> t.setGradient((Integer) v)),
            field("derive_line_color", IDiagramModelObject.class, Kind.BOOL,
                    IDiagramModelObject::getDeriveElementLineColor,
                    (t, v) -> t.setDeriveElementLineColor((Boolean) v)),
            field("icon_visible", IDiagramModelObject.class, Kind.INT,
                    IDiagramModelObject::getIconVisibleState, (t, v) -> t.setIconVisibleState((Integer) v)),
            field("icon_color", IDiagramModelObject.class, Kind.STRING,
                    IDiagramModelObject::getIconColor, (t, v) -> t.setIconColor((String) v)),
            field("figure_type", IDiagramModelArchimateObject.class, Kind.INT,
                    IDiagramModelArchimateObject::getType, (t, v) -> t.setType((Integer) v)),
            field("border_type", IBorderType.class, Kind.INT,
                    IBorderType::getBorderType, (t, v) -> t.setBorderType((Integer) v)),
            field("image_source", IDiagramModelArchimateObject.class, Kind.INT,
                    IDiagramModelArchimateObject::getImageSource, (t, v) -> t.setImageSource((Integer) v)),
            field("image_path", IIconic.class, Kind.STRING,
                    IDiagramModelImageProvider::getImagePath, (t, v) -> t.setImagePath((String) v)),
            field("image_position", IIconic.class, Kind.INT,
                    IIconic::getImagePosition, (t, v) -> t.setImagePosition((Integer) v)),
            field("hide_junction_arrows", IDiagramModelArchimateObject.class, Kind.BOOL,
                    t -> t.getFeatures().getBoolean(HIDE_JUNCTION_ARROWS,
                            IDiagramModelArchimateObject.FEATURE_HIDE_JUNCTION_ARROWS_DEFAULT),
                    (t, v) -> t.getFeatures().putBoolean(HIDE_JUNCTION_ARROWS, (Boolean) v,
                            IDiagramModelArchimateObject.FEATURE_HIDE_JUNCTION_ARROWS_DEFAULT)),
            field("name_visible", IDiagramModelConnection.class, Kind.BOOL,
                    IDiagramModelConnection::isNameVisible, (t, v) -> t.setNameVisible((Boolean) v)),
            field("label_expression", IFeatures.class, Kind.STRING,
                    t -> t.getFeatures().getString(LABEL_EXPRESSION, ""),
                    // putString with a "" default removes the feature when value equals the default.
                    (t, v) -> t.getFeatures().putString(LABEL_EXPRESSION, v == null ? "" : (String) v, "")));

    /** Style field names, for schemas and descriptions. */
    public static final List<String> KEYS = FIELDS.stream().map(Field::key).toList();

    /** One-paragraph description of the vocabulary for tool descriptions. */
    public static final String VOCABULARY = "Style fields (absent = unchanged, null or \"\" = reset to "
            + "default): fill_color, line_color, font_color, icon_color (\"#rrggbb\"); font (Archi font "
            + "string); opacity, line_opacity (0-255); line_width; line_style (figures: 0 solid, 1 dashed, "
            + "2 dotted, 3 none); text_alignment (1 left, 2 centre, 4 right); text_position (figures: 0 top, "
            + "1 centre, 2 bottom; connections: 0 source, 1 middle, 2 target); gradient (-1 none, 0-3 "
            + "direction); derive_line_color (bool); icon_visible (0 if no image, 1 always, 2 never); "
            + "figure_type (element alternate figure, 0/1); border_type (group: 0 tabbed, 1 rectangle; "
            + "note: 0 dog-ear, 1 rectangle, 2 none); image_source (0 specialization, 1 custom); "
            + "image_path (an image already in the model); image_position (0-8, 9 fill); "
            + "hide_junction_arrows (bool); name_visible (connections, bool); label_expression "
            + "(${name}, ${property:KEY}, ...). A field that doesn't apply to the target is an error.";

    private static final Map<EClass, EObject> DEFAULTS = new ConcurrentHashMap<>();

    private FigureStyle() {
    }

    private static EObject defaults(EObject target) {
        return DEFAULTS.computeIfAbsent(target.eClass(), EcoreUtil::create);
    }

    /** True if the item carries any style field. */
    public static boolean hasStyle(JsonNode item) {
        return KEYS.stream().anyMatch(item::has);
    }

    /**
     * Applies every style field present on {@code item} to {@code target}. All fields are
     * validated before any is set, so a bad field leaves the target unchanged.
     *
     * @throws Exception naming the first field that doesn't apply or has a bad value
     */
    public static void apply(JsonNode item, EObject target) throws Exception {
        apply(item, target, EcoreUtil.getRootContainer(target) instanceof IArchimateModel m ? m : null);
    }

    /** As {@link #apply(JsonNode, EObject)} for a target not yet added to {@code model}. */
    public static void apply(JsonNode item, EObject target, IArchimateModel model) throws Exception {
        List<Field> fields = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Field f : FIELDS) {
            if (!item.has(f.key())) {
                continue;
            }
            if (!f.appliesTo().test(target)) {
                throw new Exception("'" + f.key() + "' does not apply to " + target.eClass().getName());
            }
            JsonNode n = item.get(f.key());
            Object v;
            if (n.isNull() || (n.isTextual() && n.asText().isEmpty())) {
                v = f.get().apply(defaults(target));
            } else {
                v = parse(f, n);
                if (f.key().equals("image_path") && model != null && !imagePaths(model).contains(v)) {
                    throw new Exception("image_path '" + v + "' is not an image in this model "
                            + "(add the image in Archi first; importing images is not supported)");
                }
            }
            fields.add(f);
            values.add(v);
        }
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).set().accept(target, values.get(i));
        }
    }

    private static Object parse(Field f, JsonNode n) throws Exception {
        switch (f.kind()) {
            case INT:
                if (n.isIntegralNumber() && n.canConvertToInt()) {
                    return n.asInt();
                }
                if (n.isTextual() && n.asText().matches("-?\\d{1,9}")) {
                    return Integer.parseInt(n.asText());
                }
                throw new Exception("'" + f.key() + "' must be an integer");
            case BOOL:
                if (!n.isBoolean()) {
                    throw new Exception("'" + f.key() + "' must be true or false");
                }
                return n.asBoolean();
            default:
                if (!n.isTextual()) {
                    throw new Exception("'" + f.key() + "' must be a string");
                }
                return n.asText();
        }
    }

    /** Image paths referenced anywhere in the model (figures, profiles). */
    public static Set<String> imagePaths(IArchimateModel model) {
        Set<String> paths = new HashSet<>();
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof IDiagramModelImageProvider p && p.getImagePath() != null) {
                paths.add(p.getImagePath());
            }
        });
        return paths;
    }

    /**
     * Non-default style fields on {@code target}, keyed by the vocabulary names, so the map can
     * be fed straight back into {@link #apply}.
     */
    public static Map<String, Object> read(EObject target) {
        Map<String, Object> out = new LinkedHashMap<>();
        EObject def = defaults(target);
        for (Field f : FIELDS) {
            if (!f.appliesTo().test(target)) {
                continue;
            }
            Object v = f.get().apply(target);
            if (!Objects.equals(v, f.get().apply(def))) {
                out.put(f.key(), v);
            }
        }
        return out;
    }

    /** Copies every applicable style field from {@code source} to {@code target}. */
    public static void copy(EObject source, EObject target) {
        for (Field f : FIELDS) {
            if (f.appliesTo().test(source) && f.appliesTo().test(target)) {
                f.set().accept(target, f.get().apply(source));
            }
        }
    }

    /** Current label expression, or null if none is set. */
    public static String getLabelExpression(IFeatures target) {
        String v = target.getFeatures().getString(LABEL_EXPRESSION, "");
        return v.isEmpty() ? null : v;
    }
}
