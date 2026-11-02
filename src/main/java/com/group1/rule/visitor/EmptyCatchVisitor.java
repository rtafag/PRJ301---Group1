package com.group1.rule.visitor;

import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class EmptyCatchVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(CatchClause n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        if (n.getBody() == null || n.getBody().getStatements().isEmpty()) {
            int line = n.getBegin().map(p -> p.line).orElse(1);
            String paramName = n.getParameter().getNameAsString();
            arg.add(new RuleViolationResult("EMPTY_CATCH", line,
                    "Empty catch block for exception '" + paramName + "' silently swallows error.",
                    "At minimum, log the exception or rethrow it as a RuntimeException."));
        }
    }
}
