package za.co.jesseleresche.archi.mcp.testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gef.commands.CommandStack;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import za.co.jesseleresche.archi.mcp.tools.ITool;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;

/**
 * JUnit 5 extension giving each test an in-memory Archi model, registered as the open model
 * through {@link ModelAccessor#setModelSource}, with UI-thread work run inline.
 *
 * <pre>
 * &#64;RegisterExtension final TestModels models = new TestModels();
 * </pre>
 */
public final class TestModels implements BeforeEachCallback, AfterEachCallback {

    /** Lenient mapper: tests may write JSON with single quotes. */
    public static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    private final List<IArchimateModel> open = new ArrayList<>();
    public IArchimateModel model;
    public int saves;

    @Override
    public void beforeEach(ExtensionContext context) {
        UiThreadUtil.setExecutor(Runnable::run);
        ModelAccessor.setModelSource(new ModelAccessor.ModelSource() {
            @Override
            public List<IArchimateModel> getModels() {
                return open;
            }

            @Override
            public void save(IArchimateModel m) throws IOException {
                saves++;
            }
        });
        model = newModel("Test Model");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ModelAccessor.setModelSource(null);
        UiThreadUtil.setExecutor(null);
        open.clear();
    }

    /** Create another open model with the default folders. */
    public IArchimateModel newModel(String name) {
        IArchimateModel m = IArchimateFactory.eINSTANCE.createArchimateModel();
        m.setDefaults();
        m.setName(name);
        open.add(m);
        return m;
    }

    /** Close every model (getOpenModel() then returns null). */
    public void closeAll() {
        open.clear();
    }

    public static JsonNode json(String s) {
        try {
            return JSON.readTree(s);
        } catch (IOException e) {
            throw new IllegalArgumentException(s, e);
        }
    }

    /** Fail unless {@code message} is non-null and contains every one of {@code parts}. */
    public static void assertMentions(String message, String... parts) {
        for (String p : parts) {
            org.junit.jupiter.api.Assertions.assertTrue(message != null && message.contains(p),
                    "expected '" + p + "' in: " + message);
        }
    }

    /** Run a tool with (single-quote friendly) JSON args and parse the result. */
    public static JsonNode run(ITool tool, String args) throws Exception {
        return JSON.readTree(tool.execute(json(args)));
    }

    public IArchimateElement element(String type, String name) {
        EClass ec = ModelAccessor.resolveElementClass(type);
        IArchimateElement e = (IArchimateElement) IArchimateFactory.eINSTANCE.create(ec);
        e.setName(name);
        model.getDefaultFolderForObject(e).getElements().add(e);
        return e;
    }

    public IArchimateRelationship relationship(String type, IArchimateElement source, IArchimateElement target) {
        EClass ec = ModelAccessor.resolveRelationshipClass(type);
        IArchimateRelationship r = (IArchimateRelationship) IArchimateFactory.eINSTANCE.create(ec);
        r.connect(source, target);
        model.getDefaultFolderForObject(r).getElements().add(r);
        return r;
    }

    public IArchimateDiagramModel view(String name) {
        IArchimateDiagramModel v = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        v.setName(name);
        model.getDefaultFolderForObject(v).getElements().add(v);
        return v;
    }

    public IDiagramModelArchimateObject figure(IDiagramModelContainer parent, IArchimateElement e,
            int x, int y, int w, int h) {
        IDiagramModelArchimateObject f = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
        f.setArchimateElement(e);
        f.setBounds(x, y, w, h);
        parent.getChildren().add(f);
        return f;
    }

    public IDiagramModelGroup group(IDiagramModelContainer parent, String name, int x, int y, int w, int h) {
        IDiagramModelGroup g = IArchimateFactory.eINSTANCE.createDiagramModelGroup();
        g.setName(name);
        g.setBounds(x, y, w, h);
        parent.getChildren().add(g);
        return g;
    }

    public IDiagramModelNote note(IDiagramModelContainer parent, String content, int x, int y, int w, int h) {
        IDiagramModelNote n = IArchimateFactory.eINSTANCE.createDiagramModelNote();
        n.setContent(content);
        n.setBounds(x, y, w, h);
        parent.getChildren().add(n);
        return n;
    }

    public IDiagramModelArchimateConnection connection(IDiagramModelArchimateObject source,
            IDiagramModelArchimateObject target, IArchimateRelationship r) {
        IDiagramModelArchimateConnection c = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        c.setArchimateRelationship(r);
        c.connect(source, target);
        return c;
    }

    /** Install a GEF command stack on the model (tools execute through it), for undo tests. */
    public CommandStack commandStack() {
        CommandStack stack = new CommandStack();
        model.setAdapter(CommandStack.class, stack);
        return stack;
    }

    /**
     * Fail if any connection reachable from any view (through the source/target connections of
     * every diagram object or connection in it, transitively) has a null end, an end outside
     * that view, or is not itself inside that view. Deliberately independent of ModelAccessor.
     */
    public void assertNoDanglingConnections() {
        List<String> problems = new ArrayList<>();
        for (Iterator<EObject> it = model.eAllContents(); it.hasNext();) {
            if (!(it.next() instanceof IDiagramModel view)) {
                continue;
            }
            Deque<IConnectable> todo = new ArrayDeque<>();
            view.eAllContents().forEachRemaining(o -> {
                if (o instanceof IConnectable c) {
                    todo.add(c);
                }
            });
            Set<IDiagramModelConnection> seen = new HashSet<>();
            while (!todo.isEmpty()) {
                IConnectable c = todo.pop();
                List<IDiagramModelConnection> attached = new ArrayList<>(c.getSourceConnections());
                attached.addAll(c.getTargetConnections());
                for (IDiagramModelConnection conn : attached) {
                    if (!seen.add(conn)) {
                        continue;
                    }
                    todo.add(conn);
                    String id = "connection " + conn.getId() + " on view " + view.getName() + ": ";
                    if (conn.getSource() == null) {
                        problems.add(id + "null source");
                    } else if (!EcoreUtil.isAncestor(view, conn.getSource())) {
                        problems.add(id + "source not on view");
                    }
                    if (conn.getTarget() == null) {
                        problems.add(id + "null target");
                    } else if (!EcoreUtil.isAncestor(view, conn.getTarget())) {
                        problems.add(id + "target not on view");
                    }
                    if (!EcoreUtil.isAncestor(view, conn)) {
                        problems.add(id + "connection not on view");
                    }
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(problems.isEmpty(), "dangling connections: " + problems);
    }
}
