package com.group1.rule.visitor;

import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class NullReturnVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(ReturnStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        if (n.getExpression().isPresent() && n.getExpression().get() instanceof NullLiteralExpr) {
            int line = n.getBegin().map(p -> p.line).orElse(1);
            arg.add(new RuleViolationResult("NPE", line,
                    "Method returns literal null without safe nullable handling.",
                    "Return an empty collection, Optional.empty(), or document @Nullable to prevent NullPointerException."));
        }
    }
}
