package com.group1.rule.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class EqualsHashCodeVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        if (n.isInterface()) return;

        boolean hasEquals = false;
        boolean hasHashCode = false;

        for (MethodDeclaration m : n.getMethods()) {
            if ("equals".equals(m.getNameAsString()) && m.getParameters().size() == 1) {
                hasEquals = true;
            } else if ("hashCode".equals(m.getNameAsString()) && m.getParameters().isEmpty()) {
                hasHashCode = true;
            }
        }

        int line = n.getBegin().map(p -> p.line).orElse(1);
        String className = n.getNameAsString();

        if (hasEquals && !hasHashCode) {
            arg.add(new RuleViolationResult("EQUALS_HASHCODE", line,
                    "Class '" + className + "' overrides equals() but fails to override hashCode().",
                    "Always override hashCode() whenever equals() is overridden to maintain Object contract in Hash collections."));
        } else if (hasHashCode && !hasEquals) {
            arg.add(new RuleViolationResult("EQUALS_HASHCODE", line,
                    "Class '" + className + "' overrides hashCode() but fails to override equals().",
                    "Override equals() to ensure consistency with hashCode()."));
        }
    }
}
