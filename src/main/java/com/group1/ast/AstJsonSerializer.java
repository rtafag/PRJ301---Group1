package com.group1.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AstJsonSerializer {
    public static final String SCHEMA_VERSION = "1.0";
    public static final String ENGINE_NAME = "AITA-AST-Core";
    public static final String ENGINE_VERSION = "1.0.0";

    private AstJsonSerializer() {
    }

    public static String toJson(AstModel.ProjectAst project) {
        return toJson(project, true, false);
    }

    public static String toJson(AstModel.ProjectAst project, boolean includeTree, boolean pretty) {
        return MiniJson.write(toMap(project, includeTree), pretty);
    }

    public static Map<String, Object> toMap(AstModel.ProjectAst project, boolean includeTree) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        Map<String, Object> engine = new LinkedHashMap<>();
        engine.put("name", ENGINE_NAME);
        engine.put("version", ENGINE_VERSION);
        engine.put("parser", "JavaParser");
        root.put("engine", engine);
        root.put("summary", summary(project));
        List<Object> files = new ArrayList<>();
        for (AstModel.FileAst file : project.files) {
            files.add(file(file, includeTree));
        }
        root.put("files", files);
        root.put("project", projectSection(project));
        return root;
    }

    private static Map<String, Object> summary(AstModel.ProjectAst project) {
        int constructors = 0;
        int loops = 0;
        int infinite = 0;
        int lines = 0;
        int nodes = 0;
        int maxComplexity = 0;
        int nullReturns = 0;
        int allocations = 0;
        int parsed = 0;
        int fields = 0;
        for (AstModel.FileAst file : project.files) {
            lines += file.lines;
            nodes += file.nodeCount;
            loops += file.loops.size();
            nullReturns += file.nullReturns.size();
            allocations += file.allocations.size();
            if (file.syntaxValid) {
                parsed++;
            }
            for (AstModel.TypeInfo type : file.types) {
                fields += type.fieldCount;
            }
            for (AstModel.LoopInfo loop : file.loops) {
                if (loop.infiniteCandidate) {
                    infinite++;
                }
            }
            for (AstModel.MethodInfo method : file.methods) {
                if (method.constructor) {
                    constructors++;
                }
                maxComplexity = Math.max(maxComplexity, method.cyclomaticComplexity);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("syntaxValid", project.syntaxValid());
        summary.put("fileCount", project.files.size());
        summary.put("parsedFileCount", parsed);
        summary.put("skippedFileCount", project.skipped.size());
        summary.put("typeCount", project.typeCount());
        summary.put("methodCount", project.methodCount());
        summary.put("constructorCount", constructors);
        summary.put("fieldCount", fields);
        summary.put("loopCount", loops);
        summary.put("infiniteLoopCandidateCount", infinite);
        summary.put("nullReturnCount", nullReturns);
        summary.put("loopAllocationCount", allocations);
        summary.put("totalLines", lines);
        summary.put("nodeCount", nodes);
        summary.put("maxCyclomaticComplexity", maxComplexity);
        return summary;
    }

    private static Map<String, Object> file(AstModel.FileAst file, boolean includeTree) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", file.path);
        map.put("syntaxValid", file.syntaxValid);
        map.put("lines", file.lines);
        map.put("nodeCount", file.nodeCount);
        map.put("maxDepth", file.maxDepth);
        map.put("commentCount", file.commentCount);
        map.put("package", file.packageName);
        map.put("imports", file.imports);
        List<Object> errors = new ArrayList<>();
        for (AstModel.SyntaxIssue issue : file.errors) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("line", issue.line);
            item.put("column", issue.column);
            item.put("message", issue.message);
            errors.add(item);
        }
        map.put("errors", errors);
        List<Object> types = new ArrayList<>();
        for (AstModel.TypeInfo type : file.types) {
            types.add(type(type));
        }
        map.put("types", types);
        List<Object> methods = new ArrayList<>();
        for (AstModel.MethodInfo method : file.methods) {
            methods.add(method(method));
        }
        map.put("methods", methods);
        List<Object> loops = new ArrayList<>();
        for (AstModel.LoopInfo loop : file.loops) {
            loops.add(loop(loop));
        }
        map.put("loops", loops);
        List<Object> nullReturns = new ArrayList<>();
        for (AstModel.NullReturn item : file.nullReturns) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("line", item.line);
            entry.put("method", item.method);
            nullReturns.add(entry);
        }
        map.put("nullReturns", nullReturns);
        List<Object> allocations = new ArrayList<>();
        for (AstModel.Allocation item : file.allocations) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("kind", item.kind);
            entry.put("type", item.type);
            entry.put("line", item.line);
            entry.put("method", item.method);
            allocations.add(entry);
        }
        map.put("loopAllocations", allocations);
        if (includeTree && file.tree != null) {
            map.put("treeTruncated", file.treeTruncated);
            map.put("tree", tree(file.tree));
        }
        return map;
    }

    private static Map<String, Object> type(AstModel.TypeInfo type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", type.name);
        map.put("qualifiedName", type.qualifiedName);
        map.put("kind", type.kind);
        map.put("line", type.line);
        map.put("endLine", type.endLine);
        map.put("modifiers", type.modifiers);
        map.put("extends", type.extendsTypes);
        map.put("implements", type.implementsTypes);
        map.put("fieldCount", type.fieldCount);
        map.put("methodCount", type.methodCount);
        return map;
    }

    private static Map<String, Object> method(AstModel.MethodInfo method) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", method.name);
        map.put("owner", method.owner);
        map.put("signature", method.signature);
        map.put("returnType", method.returnType);
        map.put("constructor", method.constructor);
        map.put("modifiers", method.modifiers);
        List<Object> parameters = new ArrayList<>();
        for (AstModel.Param parameter : method.parameters) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", parameter.name);
            item.put("type", parameter.type);
            parameters.add(item);
        }
        map.put("parameters", parameters);
        map.put("line", method.line);
        map.put("endLine", method.endLine);
        map.put("lineCount", method.lineCount);
        map.put("statementCount", method.statementCount);
        map.put("cyclomaticComplexity", method.cyclomaticComplexity);
        map.put("maxLoopDepth", method.maxLoopDepth);
        map.put("hasBody", method.hasBody);
        map.put("hasJavadoc", method.hasJavadoc);
        map.put("hasComment", method.hasComment);
        map.put("fingerprint", method.fingerprint);
        return map;
    }

    private static Map<String, Object> loop(AstModel.LoopInfo loop) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", loop.kind);
        map.put("line", loop.line);
        map.put("endLine", loop.endLine);
        map.put("method", loop.method);
        map.put("depth", loop.depth);
        map.put("condition", loop.condition);
        map.put("constantTrueCondition", loop.constantTrueCondition);
        map.put("hasExit", loop.hasExit);
        map.put("infiniteCandidate", loop.infiniteCandidate);
        map.put("reason", loop.reason);
        return map;
    }

    private static Map<String, Object> tree(AstModel.TreeNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", node.type);
        map.put("name", node.name);
        map.put("line", node.line);
        List<Object> children = new ArrayList<>();
        for (AstModel.TreeNode child : node.children) {
            children.add(tree(child));
        }
        map.put("children", children);
        return map;
    }

    private static Map<String, Object> projectSection(AstModel.ProjectAst project) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Object> classIndex = new ArrayList<>();
        for (AstModel.ClassRef ref : project.classIndex) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("qualifiedName", ref.qualifiedName);
            item.put("kind", ref.kind);
            item.put("file", ref.file);
            classIndex.add(item);
        }
        map.put("classIndex", classIndex);
        List<Object> dependencies = new ArrayList<>();
        for (AstModel.Dependency dependency : project.dependencies) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("from", dependency.from);
            item.put("to", dependency.to);
            item.put("kind", dependency.kind);
            dependencies.add(item);
        }
        map.put("dependencies", dependencies);
        List<Object> duplicateTypes = new ArrayList<>();
        for (AstModel.DuplicateType duplicate : project.duplicateTypes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("qualifiedName", duplicate.qualifiedName);
            item.put("files", duplicate.files);
            duplicateTypes.add(item);
        }
        map.put("duplicateTypes", duplicateTypes);
        List<Object> unresolved = new ArrayList<>();
        for (AstModel.UnresolvedImport imported : project.unresolvedImports) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("file", imported.file);
            item.put("import", imported.name);
            unresolved.add(item);
        }
        map.put("unresolvedImports", unresolved);
        List<Object> duplicates = new ArrayList<>();
        for (AstModel.DuplicateGroup group : project.duplicateMethodBodies) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fingerprint", group.fingerprint);
            item.put("lineCount", group.lineCount);
            List<Object> locations = new ArrayList<>();
            for (AstModel.Location location : group.locations) {
                Map<String, Object> place = new LinkedHashMap<>();
                place.put("file", location.file);
                place.put("line", location.line);
                place.put("method", location.method);
                locations.add(place);
            }
            item.put("locations", locations);
            duplicates.add(item);
        }
        map.put("duplicateMethodBodies", duplicates);
        List<Object> skipped = new ArrayList<>();
        for (AstModel.SkippedFile file : project.skipped) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", file.path);
            item.put("reason", file.reason);
            skipped.add(item);
        }
        map.put("skippedFiles", skipped);
        return map;
    }
}
