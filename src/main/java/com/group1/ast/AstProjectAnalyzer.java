package com.group1.ast;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AstProjectAnalyzer {
    private static final int MIN_DUPLICATE_LINES = 5;
    private static final int MIN_DUPLICATE_STATEMENTS = 3;

    private final AstParser parser = new AstParser();

    public AstModel.ProjectAst analyze(Path path) throws IOException {
        SourceLoader.LoadResult loaded = SourceLoader.load(path);
        AstModel.ProjectAst project = analyze(loaded.files);
        project.skipped.addAll(loaded.skipped);
        return project;
    }

    public AstModel.ProjectAst analyze(List<AstModel.SourceFile> sources) {
        AstModel.ProjectAst project = new AstModel.ProjectAst();
        for (AstModel.SourceFile source : sources) {
            project.files.add(parser.parse(source));
        }
        Map<String, List<String>> filesByQualifiedName = new LinkedHashMap<>();
        Map<String, List<AstModel.TypeInfo>> bySimpleName = new LinkedHashMap<>();
        Set<String> packages = new HashSet<>();
        for (AstModel.FileAst file : project.files) {
            if (!file.packageName.isEmpty()) {
                packages.add(file.packageName);
            }
            for (AstModel.TypeInfo type : file.types) {
                project.classIndex.add(new AstModel.ClassRef(type.qualifiedName, type.kind, file.path));
                add(filesByQualifiedName, type.qualifiedName, file.path);
                List<AstModel.TypeInfo> same = bySimpleName.get(type.name);
                if (same == null) {
                    same = new ArrayList<>();
                    bySimpleName.put(type.name, same);
                }
                same.add(type);
            }
        }
        for (Map.Entry<String, List<String>> entry : filesByQualifiedName.entrySet()) {
            if (entry.getValue().size() > 1) {
                project.duplicateTypes.add(new AstModel.DuplicateType(entry.getKey(), entry.getValue()));
            }
        }
        resolveDependencies(project, bySimpleName);
        findUnresolvedImports(project, filesByQualifiedName.keySet(), packages);
        findDuplicateBodies(project);
        return project;
    }

    private static void add(Map<String, List<String>> map, String key, String value) {
        List<String> values = map.get(key);
        if (values == null) {
            values = new ArrayList<>();
            map.put(key, values);
        }
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static void resolveDependencies(AstModel.ProjectAst project,
            Map<String, List<AstModel.TypeInfo>> bySimpleName) {
        Set<String> emitted = new HashSet<>();
        for (AstModel.FileAst file : project.files) {
            for (AstModel.TypeInfo type : file.types) {
                for (Map.Entry<String, Set<String>> references : type.references.entrySet()) {
                    for (String simpleName : references.getValue()) {
                        AstModel.TypeInfo target = resolve(simpleName, file, bySimpleName.get(simpleName));
                        if (target == null || target.qualifiedName.equals(type.qualifiedName)) {
                            continue;
                        }
                        String key = type.qualifiedName + ">" + target.qualifiedName + ":" + references.getKey();
                        if (emitted.add(key)) {
                            project.dependencies.add(new AstModel.Dependency(type.qualifiedName,
                                    target.qualifiedName, references.getKey()));
                        }
                    }
                }
            }
        }
    }

    private static AstModel.TypeInfo resolve(String simpleName, AstModel.FileAst from,
            List<AstModel.TypeInfo> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        for (AstModel.TypeInfo candidate : candidates) {
            if (from.imports.contains(candidate.qualifiedName)) {
                return candidate;
            }
        }
        for (AstModel.TypeInfo candidate : candidates) {
            String prefix = from.packageName.isEmpty() ? "" : from.packageName + ".";
            if ((prefix + simpleName).equals(candidate.qualifiedName)) {
                return candidate;
            }
        }
        return null;
    }

    private static void findUnresolvedImports(AstModel.ProjectAst project, Set<String> known,
            Set<String> packages) {
        for (AstModel.FileAst file : project.files) {
            for (String name : file.imports) {
                if (name.startsWith("static ") || name.endsWith(".*")) {
                    continue;
                }
                int dot = name.lastIndexOf('.');
                if (dot < 0) {
                    continue;
                }
                if (packages.contains(name.substring(0, dot)) && !known.contains(name)) {
                    project.unresolvedImports.add(new AstModel.UnresolvedImport(file.path, name));
                }
            }
        }
    }

    private static void findDuplicateBodies(AstModel.ProjectAst project) {
        Map<String, AstModel.DuplicateGroup> groups = new LinkedHashMap<>();
        for (AstModel.FileAst file : project.files) {
            for (AstModel.MethodInfo method : file.methods) {
                if (!method.hasBody || method.fingerprint.isEmpty() || method.lineCount < MIN_DUPLICATE_LINES
                        || method.statementCount < MIN_DUPLICATE_STATEMENTS) {
                    continue;
                }
                AstModel.DuplicateGroup group = groups.get(method.fingerprint);
                if (group == null) {
                    group = new AstModel.DuplicateGroup(method.fingerprint, method.lineCount);
                    groups.put(method.fingerprint, group);
                }
                group.locations.add(new AstModel.Location(file.path, method.line, method.owner.isEmpty()
                        ? method.signature : method.owner + "#" + method.signature));
            }
        }
        for (AstModel.DuplicateGroup group : groups.values()) {
            if (group.locations.size() > 1) {
                project.duplicateMethodBodies.add(group);
            }
        }
    }
}
