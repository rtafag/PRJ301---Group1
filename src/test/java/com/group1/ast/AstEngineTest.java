package com.group1.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AstEngineTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final AstProjectAnalyzer analyzer = new AstProjectAnalyzer();

    private AstModel.FileAst single(String source) {
        AstModel.ProjectAst project = analyzer.analyze(
                Arrays.asList(new AstModel.SourceFile("Sample.java", source)));
        return project.files.get(0);
    }

    private AstModel.LoopInfo onlyLoop(String body) {
        AstModel.FileAst file = single("class T { void run(int n) { int k = 0; " + body + " } }");
        assertTrue(file.syntaxValid);
        assertEquals(1, file.loops.size());
        return file.loops.get(0);
    }

    @Test
    public void parsesTypesMethodsAndFields() {
        AstModel.FileAst file = single("package a.b;\nimport java.util.List;\n"
                + "public class Box implements Runnable {\n  private int size;\n  public Box() {}\n"
                + "  public void run() {}\n  int get(int a, String b) { return a; }\n}\n");
        assertTrue(file.syntaxValid);
        assertEquals("a.b", file.packageName);
        assertEquals(1, file.types.size());
        assertEquals("a.b.Box", file.types.get(0).qualifiedName);
        assertEquals("class", file.types.get(0).kind);
        assertEquals(1, file.types.get(0).fieldCount);
        assertEquals(3, file.methods.size());
        assertEquals("get(int,String)", file.methods.get(2).signature);
        assertTrue(file.methods.get(0).constructor);
        assertTrue(file.nodeCount > 10);
    }

    @Test
    public void reportsSyntaxErrorWithLine() {
        AstModel.FileAst file = single("class Broken {\n  void f( {\n  }\n}\n");
        assertFalse(file.syntaxValid);
        assertFalse(file.errors.isEmpty());
        assertEquals(2, file.errors.get(0).line);
    }

    @Test
    public void flagsConstantTrueLoopWithoutExit() {
        AstModel.LoopInfo loop = onlyLoop("while (true) { k++; }");
        assertTrue(loop.infiniteCandidate);
        assertEquals("constant-true-condition", loop.reason);
    }

    @Test
    public void flagsEmptyForHeader() {
        assertTrue(onlyLoop("for (;;) { k++; }").infiniteCandidate);
    }

    @Test
    public void flagsDoWhileTrue() {
        assertTrue(onlyLoop("do { k++; } while ((true));").infiniteCandidate);
    }

    @Test
    public void acceptsBreakReturnThrowAndSystemExit() {
        assertFalse(onlyLoop("while (true) { if (k > 3) break; k++; }").infiniteCandidate);
        assertFalse(onlyLoop("while (true) { if (k > 3) return; k++; }").infiniteCandidate);
        assertFalse(onlyLoop("while (true) { if (k > 3) throw new IllegalStateException(); }").infiniteCandidate);
        assertFalse(onlyLoop("while (true) { System.exit(1); }").infiniteCandidate);
    }

    @Test
    public void breakInsideNestedLoopOrSwitchDoesNotExitOuterLoop() {
        AstModel.FileAst file = single("class T { void run() { while (true) { for (int i = 0; i < 3; i++) { break; } } } }");
        AstModel.LoopInfo outer = file.loops.get(0);
        assertEquals("while", outer.kind);
        assertTrue(outer.infiniteCandidate);
        AstModel.FileAst withSwitch = single(
                "class T { void run(int x) { while (true) { switch (x) { case 1: break; default: x++; } } } }");
        assertTrue(withSwitch.loops.get(0).infiniteCandidate);
    }

    @Test
    public void labeledBreakCountsAsExit() {
        AstModel.FileAst file = single(
                "class T { void run() { outer: while (true) { for (;;) { break outer; } } } }");
        assertFalse(file.loops.get(0).infiniteCandidate);
    }

    @Test
    public void returnInsideLambdaDoesNotExitLoop() {
        AstModel.LoopInfo loop = onlyLoop("while (true) { Runnable r = () -> { return; }; r.run(); }");
        assertTrue(loop.infiniteCandidate);
    }

    @Test
    public void flagsConditionThatIsNeverUpdated() {
        AstModel.LoopInfo stuck = onlyLoop("int limit = 5; while (k < limit) { System.out.println(k); }");
        assertEquals("condition-never-updated", stuck.reason);
        assertTrue(stuck.infiniteCandidate);
    }

    @Test
    public void doesNotFlagUpdatedOrNonLocalConditions() {
        assertFalse(onlyLoop("while (k < n) { k += 2; }").infiniteCandidate);
        assertFalse(onlyLoop("while (k < n) { k++; }").infiniteCandidate);
        assertFalse(onlyLoop("for (int i = 0; i < n; i++) { k++; }").infiniteCandidate);
        AstModel.FileAst field = single("class T { boolean running; void run() { while (running) { } } }");
        assertFalse(field.loops.get(0).infiniteCandidate);
        AstModel.FileAst call = single("class T { void run(java.util.Iterator<String> it) { while (it.hasNext()) { } } }");
        assertFalse(call.loops.get(0).infiniteCandidate);
    }

    @Test
    public void collectsNullReturnsAndLoopAllocations() {
        AstModel.FileAst file = single("class T { String f(int n) { for (int i = 0; i < n; i++) { "
                + "int[] a = new int[8]; Object o = new Object(); } return null; } "
                + "int[] g() { return new int[2]; } }");
        assertEquals(1, file.nullReturns.size());
        assertEquals("f", file.nullReturns.get(0).method);
        assertEquals(2, file.allocations.size());
        assertEquals("int[]", file.allocations.get(0).type);
        assertEquals("array", file.allocations.get(0).kind);
        assertEquals("object", file.allocations.get(1).kind);
    }

    @Test
    public void computesComplexityAndLoopDepth() {
        AstModel.FileAst file = single("class T { int f(int a, int b) { int t = 0; "
                + "for (int i = 0; i < a; i++) { while (b > 0) { if (a > 1 && b > 1 || a < 0) { t++; } b--; } } "
                + "return a > b ? t : -t; } }");
        AstModel.MethodInfo method = file.methods.get(0);
        assertEquals(7, method.cyclomaticComplexity);
        assertEquals(2, method.maxLoopDepth);
        assertEquals(2, file.loops.get(1).depth);
    }

    @Test
    public void tracksCommentsAndJavadoc() {
        AstModel.FileAst file = single("class T {\n /** doc */\n public void a() {}\n public void b() {}\n}\n");
        assertTrue(file.methods.get(0).hasJavadoc);
        assertFalse(file.methods.get(1).hasJavadoc);
        assertEquals(1, file.commentCount);
    }

    @Test
    public void resolvesCrossFileFacts() {
        List<AstModel.SourceFile> sources = new ArrayList<>();
        sources.add(new AstModel.SourceFile("a/Main.java", "package a;\nimport b.Helper;\nimport b.Ghost;\n"
                + "public class Main { Helper helper = new Helper(); }\n"));
        sources.add(new AstModel.SourceFile("b/Helper.java", "package b;\npublic class Helper extends Base {\n"
                + "  int one(int x) {\n int y = x + 1;\n y = y * 2;\n y = y - 3;\n return y;\n }\n"
                + "  int two(int x) {\n int y = x + 1;\n y = y * 2;\n y = y - 3;\n return y;\n }\n}\n"));
        sources.add(new AstModel.SourceFile("b/Base.java", "package b;\nclass Base {}\n"));
        sources.add(new AstModel.SourceFile("c/Helper.java", "package b;\nclass Helper {}\n"));
        AstModel.ProjectAst project = analyzer.analyze(sources);
        assertEquals(4, project.files.size());
        assertEquals(1, project.duplicateTypes.size());
        assertEquals("b.Helper", project.duplicateTypes.get(0).qualifiedName);
        assertEquals(1, project.unresolvedImports.size());
        assertEquals("b.Ghost", project.unresolvedImports.get(0).name);
        assertEquals(1, project.duplicateMethodBodies.size());
        assertEquals(2, project.duplicateMethodBodies.get(0).locations.size());
        boolean extendsBase = false;
        boolean mainUsesHelper = false;
        for (AstModel.Dependency dependency : project.dependencies) {
            if (dependency.from.equals("b.Helper") && dependency.to.equals("b.Base")
                    && dependency.kind.equals("extends")) {
                extendsBase = true;
            }
            if (dependency.from.equals("a.Main") && dependency.to.equals("b.Helper")
                    && dependency.kind.equals("field")) {
                mainUsesHelper = true;
            }
        }
        assertTrue(extendsBase);
        assertTrue(mainUsesHelper);
    }

    @Test
    public void oneBrokenFileDoesNotStopTheOthers() {
        List<AstModel.SourceFile> sources = Arrays.asList(
                new AstModel.SourceFile("Ok.java", "class Ok { void f() {} }"),
                new AstModel.SourceFile("Bad.java", "class Bad { void f( { } }"));
        AstModel.ProjectAst project = analyzer.analyze(sources);
        assertFalse(project.syntaxValid());
        assertTrue(project.files.get(0).syntaxValid);
        assertEquals("Bad.java", project.firstErrorFile());
        assertEquals(1, project.methodCount());
    }

    @Test
    public void loadsZipAndSkipsUnsafeEntries() throws IOException {
        File zip = temp.newFile("sub.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, "src/Good.java", "class Good {}");
            put(out, "../evil/Evil.java", "class Evil {}");
            put(out, "/abs/Abs.java", "class Abs {}");
            put(out, "readme.txt", "text");
            put(out, "__MACOSX/._Good.java", "junk");
            put(out, "target/Generated.java", "class Generated {}");
        }
        SourceLoader.LoadResult result = SourceLoader.load(zip.toPath());
        assertEquals(1, result.files.size());
        assertEquals("src/Good.java", result.files.get(0).path);
        assertEquals(2, result.skipped.size());
        AstModel.ProjectAst project = analyzer.analyze(zip.toPath());
        assertEquals(2, project.skipped.size());
        assertTrue(project.syntaxValid());
    }

    @Test
    public void rejectsArchivesWithTooManyEntries() throws IOException {
        File zip = temp.newFile("many.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (int i = 0; i <= SourceLoader.MAX_ARCHIVE_ENTRIES; i++) {
                put(out, "f" + i + ".txt", "x");
            }
        }
        try {
            SourceLoader.load(zip.toPath());
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("too many"));
        }
    }

    @Test
    public void loadsDirectoryRecursively() throws IOException {
        File root = temp.newFolder("proj");
        File pkg = new File(root, "p");
        assertTrue(pkg.mkdirs());
        write(new File(pkg, "A.java"), "package p; class A {}");
        write(new File(root, "B.java"), "class B {}");
        write(new File(root, "notes.md"), "x");
        Path path = root.toPath();
        SourceLoader.LoadResult result = SourceLoader.load(path);
        assertEquals(2, result.files.size());
        assertEquals("B.java", result.files.get(0).path);
        assertEquals("p/A.java", result.files.get(1).path);
    }

    @Test
    public void jsonFollowsSchemaShape() {
        AstModel.ProjectAst project = analyzer.analyze(Arrays.asList(new AstModel.SourceFile("Q.java",
                "class Q { String s() { return \"line\\n\\\"quoted\\\"\"; } void w() { while (true) { } } }")));
        String json = AstJsonSerializer.toJson(project);
        assertTrue(json.startsWith("{\"schemaVersion\":\"1.0\""));
        assertTrue(json.contains("\"summary\":{"));
        assertTrue(json.contains("\"infiniteLoopCandidateCount\":1"));
        assertTrue(json.contains("\"reason\":\"constant-true-condition\""));
        assertTrue(json.contains("\"tree\":{"));
        assertFalse(AstJsonSerializer.toJson(project, false, false).contains("\"tree\""));
        assertTrue(AstJsonSerializer.toJson(project, false, true).contains("\n  \"engine\""));
        assertNotNull(project.files.get(0).loops.get(0));
    }

    @Test
    public void treeStaysBounded() {
        StringBuilder source = new StringBuilder("class Deep { void f(int a) {");
        for (int i = 0; i < 40; i++) {
            source.append("if (a > ").append(i).append(") {");
        }
        for (int i = 0; i < 40; i++) {
            source.append("}");
        }
        source.append("} }");
        AstModel.FileAst file = single(source.toString());
        assertTrue(file.syntaxValid);
        assertTrue(file.treeTruncated);
        assertTrue(depth(file.tree) <= AstParser.MAX_TREE_DEPTH + 1);
    }

    private static int depth(AstModel.TreeNode node) {
        int deepest = 0;
        for (AstModel.TreeNode child : node.children) {
            deepest = Math.max(deepest, depth(child));
        }
        return deepest + 1;
    }

    private static void put(ZipOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void write(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
