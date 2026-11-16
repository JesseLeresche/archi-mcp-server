import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;

/**
 * Replaces every method and constructor body under the given source directories with
 * {@code throw new UnsupportedOperationException("stub")}, keeping signatures (and any
 * explicit this(...)/super(...) call) so the module still compiles. Used to prove that
 * characterisation tests go red without the real code and green with it.
 *
 * <pre>
 * mvn dependency:get -Dartifact=com.github.javaparser:javaparser-core:3.26.3
 * java -cp ~/.m2/repository/com/github/javaparser/javaparser-core/3.26.3/javaparser-core-3.26.3.jar \
 *      scripts/StubBodies.java src/za/.../tools src/za/.../util src/za/.../protocol
 * </pre>
 */
public class StubBodies {

    private static final String THROW = "throw new UnsupportedOperationException(\"stub\");";

    public static void main(String[] args) throws Exception {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        for (String dir : args) {
            List<Path> files;
            try (Stream<Path> s = Files.walk(Path.of(dir))) {
                files = s.filter(p -> p.toString().endsWith(".java")).toList();
            }
            for (Path f : files) {
                CompilationUnit cu = StaticJavaParser.parse(f);
                for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                    if (m.getBody().isPresent()) {
                        m.setBody(stub());
                    }
                }
                for (ConstructorDeclaration c : cu.findAll(ConstructorDeclaration.class)) {
                    BlockStmt body = stub();
                    List<Statement> old = c.getBody().getStatements();
                    if (!old.isEmpty() && old.get(0) instanceof ExplicitConstructorInvocationStmt call) {
                        body.getStatements().addFirst(call.clone());
                    }
                    c.setBody(body);
                }
                Files.writeString(f, cu.toString());
                System.out.println("stubbed " + f);
            }
        }
    }

    private static BlockStmt stub() {
        return new BlockStmt().addStatement(StaticJavaParser.parseStatement(THROW));
    }
}
