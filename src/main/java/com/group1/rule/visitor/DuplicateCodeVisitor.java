package com.group1.rule.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;

import java.util.List;

public class DuplicateCodeVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(ClassOrInterfaceDeclaration n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        List<MethodDeclaration> methods = n.getMethods();
        if (methods.size() < 2) return;

        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration m1 = methods.get(i);
            if (!m1.getBody().isPresent()) continue;
            String body1 = normalizeBody(m1.getBody().get().toString());
            if (body1.length() < 50) continue;

            for (int j = i + 1; j < methods.size(); j++) {
                MethodDeclaration m2 = methods.get(j);
                if (!m2.getBody().isPresent()) continue;
                String body2 = normalizeBody(m2.getBody().get().toString());

                if (body1.equals(body2)) {
                    int line = m2.getBegin().map(p -> p.line).orElse(1);
                    arg.add(new RuleViolationResult("DUPLICATE_CODE", line,
                            "Method '" + m2.getNameAsString() + "' duplicates the implementation body of method '" + m1.getNameAsString() + "'.",
                            "Extract repeated logic into a shared private helper method to eliminate duplicate code."));
                }
            }
        }
    }

    private String normalizeBody(String body) {
        return body.replaceAll("/\\*.*?\\*/", "")
                   .replaceAll("//.*", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
