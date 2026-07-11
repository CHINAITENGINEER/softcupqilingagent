package com.cup.opsagent.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MilvusSdkBoundaryTest {

    private static final String SDK_REFERENCE = "io/milvus";
    private static final String RAG_INFRASTRUCTURE_PATH = "com/cup/opsagent/rag/";

    @Test
    void shouldKeepMilvusSdkReferencesInsideTheSdkAdapter() throws IOException, URISyntaxException {
        Path classesRoot = Path.of(MilvusSdkBoundaryTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
                .resolveSibling("classes");

        List<String> violations;
        try (var classFiles = Files.walk(classesRoot)) {
            violations = classFiles
                    .filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !normalizedRelativePath(classesRoot, path).startsWith(RAG_INFRASTRUCTURE_PATH))
                    .filter(this::referencesMilvusSdk)
                    .map(classesRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertThat(violations)
                .as("Milvus SDK must not leak outside the RAG infrastructure package")
                .isEmpty();
    }

    private String normalizedRelativePath(Path classesRoot, Path classFile) {
        return classesRoot.relativize(classFile).toString().replace('\\', '/');
    }

    private boolean referencesMilvusSdk(Path classFile) {
        try {
            String constantPool = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
            return constantPool.contains(SDK_REFERENCE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect compiled class " + classFile, exception);
        }
    }
}
