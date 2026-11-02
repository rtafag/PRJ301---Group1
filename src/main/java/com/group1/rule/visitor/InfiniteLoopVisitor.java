package com.group1.rule.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InfiniteLoopVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(WhileStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        int line = n.getBegin().map(p -> p.line).orElse(1);

        // Case 1: while (true) without break/return/throw
        if (n.getCondition() instanceof BooleanLiteralExpr) {
            BooleanLiteralExpr boolExpr = (BooleanLiteralExpr) n.getCondition();
            if (boolExpr.getValue() && !hasLoopExit(n.getBody())) {
                arg.add(new RuleViolationResult("INF_LOOP", line,
                        "while(true) loop lacks an exit condition (break, return, or throw).",
                        "Add a break or return statement inside a conditional check within the loop body."));
                return;
            }
        }

        // Case 2: while (var < N) where var is never updated in the body
        if (n.getCondition() instanceof BinaryExpr) {
            BinaryExpr bin = (BinaryExpr) n.getCondition();
            Set<String> condVars = new HashSet<>();
            bin.findAll(NameExpr.class).forEach(name -> condVars.add(name.getNameAsString()));

            if (!condVars.isEmpty() && !hasLoopExit(n.getBody())) {
                boolean modified = false;
                for (String v : condVars) {
                    // Check if variable is updated via unary (i++, ++i, i--, --i)
                    for (UnaryExpr u : n.getBody().findAll(UnaryExpr.class)) {
                        if (u.getExpression() instanceof NameExpr &&
                                ((NameExpr) u.getExpression()).getNameAsString().equals(v)) {
                            modified = true;
                            break;
                        }
                    }
                    if (modified) break;

                    // Check if variable is target of assignment (i = ..., i += ...)
                    for (AssignExpr a : n.getBody().findAll(AssignExpr.class)) {
                        if (a.getTarget() instanceof NameExpr &&
                                ((NameExpr) a.getTarget()).getNameAsString().equals(v)) {
                            modified = true;
                            break;
                        }
                    }
                    if (modified) break;
                }

                if (!modified) {
                    arg.add(new RuleViolationResult("INF_LOOP", line,
                            "Loop condition variables " + condVars + " are never modified inside the loop body.",
                            "Ensure loop control variables are updated or provide an explicit break condition."));
                }
            }
        }
    }

    @Override
    public void visit(ForStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        int line = n.getBegin().map(p -> p.line).orElse(1);

        // Case 3: for (;;) or for (init; true; ...) without break/return/throw
        boolean conditionAlwaysTrue = !n.getCompare().isPresent()
                || (n.getCompare().get() instanceof BooleanLiteralExpr
                        && ((BooleanLiteralExpr) n.getCompare().get()).getValue());

        if (conditionAlwaysTrue && !hasLoopExit(n.getBody())) {
            arg.add(new RuleViolationResult("INF_LOOP", line,
                    "for loop condition is empty or always true without an exit statement.",
                    "Provide a termination condition or add a break/return inside the loop."));
        }
    }

    @Override
    public void visit(DoStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        int line = n.getBegin().map(p -> p.line).orElse(1);

        if (n.getCondition() instanceof BooleanLiteralExpr) {
            BooleanLiteralExpr boolExpr = (BooleanLiteralExpr) n.getCondition();
            if (boolExpr.getValue() && !hasLoopExit(n.getBody())) {
                arg.add(new RuleViolationResult("INF_LOOP", line,
                        "do-while(true) loop lacks an exit condition (break, return, or throw).",
                        "Add a break or return statement inside the do-while body."));
            }
        }
    }

    private boolean hasLoopExit(Node body) {
        if (body == null) return false;
        boolean hasBreak = !body.findAll(BreakStmt.class).isEmpty();
        boolean hasReturn = !body.findAll(ReturnStmt.class).isEmpty();
        boolean hasThrow = !body.findAll(ThrowStmt.class).isEmpty();
        return hasBreak || hasReturn || hasThrow;
    }
}
