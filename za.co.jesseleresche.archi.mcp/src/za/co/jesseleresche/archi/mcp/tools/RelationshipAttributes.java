package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IInfluenceRelationship;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The one place that reads, validates and sets the type-specific relationship attributes:
 * {@code access_type} (Access, by name or Archi's integer), {@code influence_strength}
 * (Influence) and {@code directed} (Association).
 */
final class RelationshipAttributes {

    static final List<String> FIELDS = List.of("access_type", "influence_strength", "directed");

    private static final Map<String, Integer> ACCESS = new LinkedHashMap<>();
    static {
        ACCESS.put("Write", IAccessRelationship.WRITE_ACCESS);
        ACCESS.put("Read", IAccessRelationship.READ_ACCESS);
        ACCESS.put("Unspecified", IAccessRelationship.UNSPECIFIED_ACCESS);
        ACCESS.put("ReadWrite", IAccessRelationship.READ_WRITE_ACCESS);
    }

    /** e.g. "0=Write, 1=Read, 2=Unspecified, 3=ReadWrite", built from Archi's constants. */
    static final String ACCESS_LEGEND;
    static {
        StringBuilder b = new StringBuilder();
        ACCESS.forEach((name, v) -> b.append(b.length() == 0 ? "" : ", ").append(v).append('=').append(name));
        ACCESS_LEGEND = b.toString();
    }

    private RelationshipAttributes() {
    }

    /** The three attributes' input schema, shared by create and update. */
    static void addSchema(ObjectNode props) {
        ObjectNode at = props.putObject("access_type");
        at.putArray("type").add("string").add("integer");
        at.put("description", "AccessRelationship only: Write, Read, Unspecified or ReadWrite "
                + "(case-insensitive), or Archi's integer " + ACCESS_LEGEND + ". New Access relationships "
                + "default to Write.");
        props.putObject("influence_strength").put("type", "string")
                .put("description", "InfluenceRelationship only: strength, e.g. \"+\", \"++\", \"-\"; \"\" clears it.");
        props.putObject("directed").put("type", "boolean")
                .put("description", "AssociationRelationship only: true for a directed association.");
    }

    static boolean appliesTo(String field, EClass eClass) {
        IArchimatePackage p = IArchimatePackage.eINSTANCE;
        return switch (field) {
            case "access_type" -> eClass == p.getAccessRelationship();
            case "influence_strength" -> eClass == p.getInfluenceRelationship();
            case "directed" -> eClass == p.getAssociationRelationship();
            default -> false;
        };
    }

    /** Throws if a given field doesn't apply to {@code eClass} or has a bad value. Mutates nothing. */
    static void check(EClass eClass, JsonNode item) throws Exception {
        for (String f : FIELDS) {
            if (item.hasNonNull(f) && !appliesTo(f, eClass)) {
                throw new Exception("'" + f + "' does not apply to " + eClass.getName());
            }
        }
        if (item.hasNonNull("access_type")) {
            accessType(item.get("access_type"));
        }
        if (item.hasNonNull("directed") && !item.get("directed").isBoolean()) {
            throw new Exception("directed must be true or false, got " + item.get("directed"));
        }
    }

    /** Sets whichever attributes the item gives; assumes {@link #check} passed. */
    static void apply(IArchimateRelationship rel, JsonNode item) throws Exception {
        if (item.hasNonNull("access_type") && rel instanceof IAccessRelationship a) {
            a.setAccessType(accessType(item.get("access_type")));
        }
        if (item.hasNonNull("influence_strength") && rel instanceof IInfluenceRelationship i) {
            i.setStrength(item.get("influence_strength").asText());
        }
        if (item.hasNonNull("directed") && rel instanceof IAssociationRelationship a) {
            a.setDirected(item.get("directed").asBoolean());
        }
    }

    /** Puts the attributes in create's input form: access type always (as a name), the others only if set. */
    static void read(IArchimateRelationship rel, Map<String, Object> out) {
        if (rel instanceof IAccessRelationship a) {
            out.put("access_type", ACCESS.entrySet().stream().filter(e -> e.getValue() == a.getAccessType())
                    .map(Map.Entry::getKey).findFirst().orElse(String.valueOf(a.getAccessType())));
        }
        if (rel instanceof IInfluenceRelationship i && i.getStrength() != null && !i.getStrength().isEmpty()) {
            out.put("influence_strength", i.getStrength());
        }
        if (rel instanceof IAssociationRelationship a && a.isDirected()) {
            out.put("directed", true);
        }
    }

    private static int accessType(JsonNode n) throws Exception {
        if (n.isTextual()) {
            for (var e : ACCESS.entrySet()) {
                if (e.getKey().equalsIgnoreCase(n.asText())) {
                    return e.getValue();
                }
            }
        } else if (n.isIntegralNumber() && ACCESS.containsValue(n.asInt())) {
            return n.asInt();
        }
        throw new Exception("access_type must be one of Write, Read, Unspecified, ReadWrite or 0-3, got " + n.asText());
    }
}
