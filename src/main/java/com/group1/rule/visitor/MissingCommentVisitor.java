package com.group1.rule.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class MissingCommentVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(MethodDeclaration n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        // Check only public methods that are not override methods
        if (n.isPublic() && !n.getAnnotationByName("Override").isPresent()) {
            if (!n.getComment().isPresent()) {
                int line = n.getBegin().map(p -> p.line).orElse(1);
                String methodName = n.getNameAsString();
                arg.add(new RuleViolationResult("COMMENT", line,
                        "Public method '" + methodName + "' is missing Javadoc or descriptive comment.",
                        "Add a concise Javadoc explaining method purpose, parameters, and return value."));
            }
        }
    }
}
