package za.co.jesseleresche.archi.mcp.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;

/**
 * Removes diagram objects (with their whole subtree) and connections, together with every
 * connection attached at either end to anything removed, transitively — so nothing is left
 * dangling. Undoable. Runs inside a caller's Command; never saves.
 *
 * <p>Collection happens in the constructor, so a dry run can read the counts without executing.
 * Never uses {@code EcoreUtil.delete}: it nulls references that undo needs.
 */
public final class ConnectableRemoval {

    private final List<IDiagramModelObject> roots = new ArrayList<>();
    private final Set<IDiagramModelConnection> connections;
    private final List<IDiagramModelContainer> parents = new ArrayList<>();
    private final List<Integer> indexes = new ArrayList<>();

    public ConnectableRemoval(Collection<? extends IConnectable> removed) {
        Set<IConnectable> gathered = new LinkedHashSet<>();
        for (IConnectable r : removed) {
            gathered.add(r);
            r.eAllContents().forEachRemaining(o -> {
                if (o instanceof IConnectable c) {
                    gathered.add(c);
                }
            });
            if (r instanceof IDiagramModelObject dmo && !roots.contains(dmo)) {
                roots.add(dmo);
            }
        }
        roots.removeIf(r -> roots.stream().anyMatch(o -> o != r && EcoreUtil.isAncestor(o, r)));
        connections = ModelAccessor.attachedConnections(gathered);
    }

    /** Distinct connections this removal takes out. */
    public int connectionCount() {
        return connections.size();
    }

    /** Top-level diagram objects this removal takes out (nested ones leave with them). */
    public int rootCount() {
        return roots.size();
    }

    public void execute() {
        connections.forEach(IDiagramModelConnection::disconnect);
        parents.clear();
        indexes.clear();
        for (IDiagramModelObject r : roots) {
            IDiagramModelContainer parent = (IDiagramModelContainer) r.eContainer();
            parents.add(parent);
            indexes.add(parent.getChildren().indexOf(r));
            parent.getChildren().remove(r);
        }
    }

    public void undo() {
        for (int i = roots.size() - 1; i >= 0; i--) {
            parents.get(i).getChildren().add(indexes.get(i), roots.get(i));
        }
        // disconnect() keeps source/target, so reconnect() re-attaches both ends.
        // ponytail: re-attached connections go to the end of their source/target lists; record
        // indexes if list order ever matters.
        connections.forEach(IDiagramModelConnection::reconnect);
    }
}
