package org.eclipse.core.runtime;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Path;

import org.osgi.framework.Bundle;

/**
 * Test-only shadow of Eclipse's Platform (test classes precede the target platform on the
 * classpath). Archi's RelationshipsMatrix loads model/relationships.xml through
 * Platform.getBundle(...).getEntry(...), which needs a running OSGi framework. This
 * resolves entries against the directory holding the Archi bundle's jar instead.
 */
public final class Platform {

    private Platform() {
    }

    public static Bundle getBundle(String symbolicName) {
        return (Bundle) Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[] {Bundle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getEntry" -> entry(symbolicName, (String) args[0]);
                    case "getSymbolicName" -> symbolicName;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TestBundle[" + symbolicName + "]";
                    default -> throw new UnsupportedOperationException("test Platform: Bundle." + method.getName());
                });
    }

    private static URL entry(String bundle, String path) throws Exception {
        if (!"com.archimatetool.model".equals(bundle)) {
            return null;
        }
        // Archi bundle dirs hold an inner <bundle>.jar; entries sit beside it.
        URL jar = Class.forName("com.archimatetool.model.IArchimatePackage")
                .getProtectionDomain().getCodeSource().getLocation();
        Path f = Path.of(jar.toURI()).getParent().resolve(path);
        return f.toFile().exists() ? f.toUri().toURL() : null;
    }
}
