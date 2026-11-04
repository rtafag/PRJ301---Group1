package com.group1.ast;

import com.github.javaparser.ast.CompilationUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AstModel {
    private AstModel() {
    }

    public static final class SourceFile {
        public final String path;
        public final String content;

        public SourceFile(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    public static final class SkippedFile {
        public final String path;
        public final String reason;

        public SkippedFile(String path, String reason) {
            this.path = path;
            this.reason = reason;
        }
    }

    public static final class SyntaxIssue {
        public final int line;
        public final int column;
        public final String message;

        public SyntaxIssue(int line, int column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }
    }

    public static final class Param {
        public final String name;
        public final String type;

        public Param(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static final class TypeInfo {
        public String name = "";
        public String qualifiedName = "";
        public String kind = "class";
        public int line;
        public int endLine;
        public int fieldCount;
        public int methodCount;
        public final List<String> modifiers = new ArrayList<>();
        public final List<String> extendsTypes = new ArrayList<>();
        public final List<String> implementsTypes = new ArrayList<>();
        public final Map<String, Set<String>> references = new LinkedHashMap<>();

        public void reference(String kind, String typeName) {
            Set<String> targets = references.get(kind);
            if (targets == null) {
                targets = new LinkedHashSet<>();
                references.put(kind, targets);
            }
            targets.add(typeName);
        }
    }

    public static final class MethodInfo {
        public String name = "";
        public String owner = "";
        public String signature = "";
        public String returnType = "";
        public String fingerprint = "";
        public int line;
        public int endLine;
        public int lineCount;
        public int statementCount;
        public int cyclomaticComplexity = 1;
        public int maxLoopDepth;
        public boolean constructor;
        public boolean hasBody;
        public boolean hasJavadoc;
        public boolean hasComment;
        public final List<Param> parameters = new ArrayList<>();
        public final List<String> modifiers = new ArrayList<>();
    }

    public static final class LoopInfo {
        public String kind = "";
        public String method = "";
        public String condition = "";
        public String reason;
        public int line;
        public int endLine;
        public int depth = 1;
        public boolean constantTrueCondition;
        public boolean hasExit;
        public boolean infiniteCandidate;
    }

    public static final class Allocation {
        public final String kind;
        public final String type;
        public final String method;
        public final int line;

        public Allocation(String kind, String type, String method, int line) {
            this.kind = kind;
            this.type = type;
            this.method = method;
            this.line = line;
        }
    }

    public static final class NullReturn {
        public final String method;
        public final int line;

        public NullReturn(String method, int line) {
            this.method = method;
            this.line = line;
        }
    }

    public static final class TreeNode {
        public final String type;
        public final String name;
        public final int line;
        public final List<TreeNode> children = new ArrayList<>();

        public TreeNode(String type, String name, int line) {
            this.type = type;
            this.name = name;
            this.line = line;
        }
    }

    public static final class FileAst {
        public final String path;
        public boolean syntaxValid;
        public int lines;
        public int nodeCount;
        public int maxDepth;
        public int commentCount;
        public boolean treeTruncated;
        public String packageName = "";
        public TreeNode tree;
        public final List<String> imports = new ArrayList<>();
        public final List<SyntaxIssue> errors = new ArrayList<>();
        public final List<TypeInfo> types = new ArrayList<>();
        public final List<MethodInfo> methods = new ArrayList<>();
        public final List<LoopInfo> loops = new ArrayList<>();
        public final List<NullReturn> nullReturns = new ArrayList<>();
        public final List<Allocation> allocations = new ArrayList<>();
        private CompilationUnit unit;

        public FileAst(String path) {
            this.path = path;
        }

        public CompilationUnit unit() {
            return unit;
        }

        public void attach(CompilationUnit unit) {
            this.unit = unit;
        }
    }

    public static final class Dependency {
        public final String from;
        public final String to;
        public final String kind;

        public Dependency(String from, String to, String kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }

    public static final class ClassRef {
        public final String qualifiedName;
        public final String kind;
        public final String file;

        public ClassRef(String qualifiedName, String kind, String file) {
            this.qualifiedName = qualifiedName;
            this.kind = kind;
            this.file = file;
        }
    }

    public static final class DuplicateType {
        public final String qualifiedName;
        public final List<String> files;

        public DuplicateType(String qualifiedName, List<String> files) {
            this.qualifiedName = qualifiedName;
            this.files = files;
        }
    }

    public static final class UnresolvedImport {
        public final String file;
        public final String name;

        public UnresolvedImport(String file, String name) {
            this.file = file;
            this.name = name;
        }
    }

    public static final class Location {
        public final String file;
        public final int line;
        public final String method;

        public Location(String file, int line, String method) {
            this.file = file;
            this.line = line;
            this.method = method;
        }
    }

    public static final class DuplicateGroup {
        public final String fingerprint;
        public final int lineCount;
        public final List<Location> locations = new ArrayList<>();

        public DuplicateGroup(String fingerprint, int lineCount) {
            this.fingerprint = fingerprint;
            this.lineCount = lineCount;
        }
    }

    public static final class ProjectAst {
        public final List<FileAst> files = new ArrayList<>();
        public final List<SkippedFile> skipped = new ArrayList<>();
        public final List<ClassRef> classIndex = new ArrayList<>();
        public final List<Dependency> dependencies = new ArrayList<>();
        public final List<DuplicateType> duplicateTypes = new ArrayList<>();
        public final List<UnresolvedImport> unresolvedImports = new ArrayList<>();
        public final List<DuplicateGroup> duplicateMethodBodies = new ArrayList<>();

        public boolean syntaxValid() {
            if (files.isEmpty()) {
                return false;
            }
            for (FileAst file : files) {
                if (!file.syntaxValid) {
                    return false;
                }
            }
            return true;
        }

        public int typeCount() {
            int total = 0;
            for (FileAst file : files) {
                total += file.types.size();
            }
            return total;
        }

        public int methodCount() {
            int total = 0;
            for (FileAst file : files) {
                for (MethodInfo method : file.methods) {
                    if (!method.constructor) {
                        total++;
                    }
                }
            }
            return total;
        }

        public SyntaxIssue firstError() {
            for (FileAst file : files) {
                if (!file.errors.isEmpty()) {
                    return file.errors.get(0);
                }
            }
            return null;
        }

        public String firstErrorFile() {
            for (FileAst file : files) {
                if (!file.errors.isEmpty()) {
                    return file.path;
                }
            }
            return null;
        }
    }
}
