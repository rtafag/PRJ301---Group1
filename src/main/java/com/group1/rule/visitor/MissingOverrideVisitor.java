package com.group1.rule.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MissingOverrideVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    private static final Set<String> OVERRIDE_METHOD_NAMES = new HashSet<>(Arrays.asList(
            "equals", "hashCode", "toString", "compareTo",
            "doGet", "doPost", "doPut", "doDelete", "service", "init", "destroy"
    ));

    @Override
    public void visit(MethodDeclaration n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        String name = n.getNameAsString();
        if (OVERRIDE_METHOD_NAMES.contains(name)) {
            boolean hasOverride = n.getAnnotationByName("Override").isPresent();
            if (!hasOverride) {
                int line = n.getBegin().map(p -> p.line).orElse(1);
                arg.add(new RuleViolationResult("MISSING_OVERRIDE", line,
                        "Standard method '" + name + "' overrides a superclass or interface method but lacks @Override annotation.",
                        "Add @Override annotation before the method declaration to prevent signature errors."));
            }
        }
    }
}
