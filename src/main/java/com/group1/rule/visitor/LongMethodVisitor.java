package com.group1.rule.visitor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class LongMethodVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    private static final int MAX_LINES = 40;

    @Override
    public void visit(MethodDeclaration n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        if (n.getBegin().isPresent() && n.getEnd().isPresent()) {
            int lineCount = n.getEnd().get().line - n.getBegin().get().line + 1;
            if (lineCount > MAX_LINES) {
                int line = n.getBegin().get().line;
                String methodName = n.getNameAsString();
                arg.add(new RuleViolationResult("LONG_METHOD", line,
                        "Method '" + methodName + "' exceeds maximum recommended length (" + lineCount + " > " + MAX_LINES + " lines).",
                        "Extract complex logic into smaller, single-purpose helper methods."));
            }
        }
    }
}
