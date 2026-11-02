package com.group1.rule.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class MemoryLeakVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(ForStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkLoopBody(n.getBody(), arg);
    }

    @Override
    public void visit(ForEachStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkLoopBody(n.getBody(), arg);
    }

    @Override
    public void visit(WhileStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkLoopBody(n.getBody(), arg);
    }

    @Override
    public void visit(DoStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkLoopBody(n.getBody(), arg);
    }

    private void checkLoopBody(Node body, List<RuleViolationResult> arg) {
        if (body == null) return;
        List<ArrayCreationExpr> arrays = body.findAll(ArrayCreationExpr.class);
        for (ArrayCreationExpr a : arrays) {
            int line = a.getBegin().map(p -> p.line).orElse(1);
            arg.add(new RuleViolationResult("OOM", line,
                    "Repeated array allocation inside loop body may lead to excessive memory consumption / OutOfMemoryError.",
                    "Pre-allocate memory buffers or arrays outside the loop whenever possible."));
        }
    }
}
