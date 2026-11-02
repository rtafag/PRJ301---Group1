package com.group1.rule.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class NamingConventionVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        String name = n.getNameAsString();
        if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            int line = n.getBegin().map(p -> p.line).orElse(1);
            arg.add(new RuleViolationResult("CS", line,
                    "Class or interface name '" + name + "' starts with a lowercase letter.",
                    "Use PascalCase convention (uppercase first letter) for Java class and interface names."));
        }
    }
}
