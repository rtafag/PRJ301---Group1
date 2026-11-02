package com.group1.rule.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
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
            if (boolExpr.getValue() && !hasLoopExit(n, n.getBody())) {
                arg.add(new RuleViolationResult("INF_LOOP", line,
                        "while(true) loop lacks an exit condition (break, return, or throw).",
                        "Add a conditional check with break or return inside the while body to allow termination."));
                return;
            }
        }

        // Case 2: while (var < N) where var is never updated in the body
        if (n.getCondition() instanceof BinaryExpr) {
            BinaryExpr bin = (BinaryExpr) n.getCondition();
            Set<String> condVars = extractVariableNames(bin);

            if (!condVars.isEmpty() && !hasLoopExit(n, n.getBody())) {
                if (!isAnyVariableModified(condVars, n.getBody())) {
                    arg.add(new RuleViolationResult("INF_LOOP", line,
                            "Loop condition variables " + condVars + " are never updated inside the while loop body.",
                            "Update the loop control variables inside the loop or add an explicit break condition."));
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

        if (conditionAlwaysTrue && !hasLoopExit(n, n.getBody())) {
            arg.add(new RuleViolationResult("INF_LOOP", line,
                    "for loop condition is empty or always true without an exit statement.",
                    "Provide a valid termination condition or add a break/return inside the loop."));
            return;
        }

        // Case 4: for loop counter moving in WRONG DIRECTION (e.g. for(int i=0; i < 10; i--))
        if (n.getCompare().isPresent() && n.getCompare().get() instanceof BinaryExpr) {
            BinaryExpr compare = (BinaryExpr) n.getCompare().get();
            BinaryExpr.Operator op = compare.getOperator();

            for (Expression update : n.getUpdate()) {
                if (update instanceof UnaryExpr) {
                    UnaryExpr unary = (UnaryExpr) update;
                    if (unary.getExpression() instanceof NameExpr) {
                        String varName = ((NameExpr) unary.getExpression()).getNameAsString();

                        // Check if this variable is in the comparison
                        if (compare.getLeft() instanceof NameExpr &&
                                ((NameExpr) compare.getLeft()).getNameAsString().equals(varName)) {

                            boolean isLess = (op == BinaryExpr.Operator.LESS || op == BinaryExpr.Operator.LESS_EQUALS);
                            boolean isGreater = (op == BinaryExpr.Operator.GREATER || op == BinaryExpr.Operator.GREATER_EQUALS);

                            boolean isDecrement = (unary.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT ||
                                                   unary.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT);
                            boolean isIncrement = (unary.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT ||
                                                   unary.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT);

                            if (isLess && isDecrement) {
                                arg.add(new RuleViolationResult("INF_LOOP", line,
                                        "Loop counter '" + varName + "' is decremented ('" + varName + "--') but condition checks '" + varName + " < ...'. It will run infinitely.",
                                        "Change the update expression to increment ('" + varName + "++') or adjust the condition."));
                                return;
                            } else if (isGreater && isIncrement) {
                                arg.add(new RuleViolationResult("INF_LOOP", line,
                                        "Loop counter '" + varName + "' is incremented ('" + varName + "++') but condition checks '" + varName + " > ...'. It will run infinitely.",
                                        "Change the update expression to decrement ('" + varName + "--') or adjust the condition."));
                                return;
                            }
                        }
                    }
                }
            }

            // Case 5: for (init; i < 10; ) where update is empty and i is never updated in body
            if (n.getUpdate().isEmpty() && !hasLoopExit(n, n.getBody())) {
                Set<String> condVars = extractVariableNames(compare);
                if (!condVars.isEmpty() && !isAnyVariableModified(condVars, n.getBody())) {
                    arg.add(new RuleViolationResult("INF_LOOP", line,
                            "for loop has no update statement and condition variables " + condVars + " are never modified in the body.",
                            "Add an update expression in the for loop header or update variables inside the body."));
                }
            }
        }
    }

    @Override
    public void visit(DoStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        int line = n.getBegin().map(p -> p.line).orElse(1);

        // Case 6: do ... while(true)
        if (n.getCondition() instanceof BooleanLiteralExpr) {
            BooleanLiteralExpr boolExpr = (BooleanLiteralExpr) n.getCondition();
            if (boolExpr.getValue() && !hasLoopExit(n, n.getBody())) {
                arg.add(new RuleViolationResult("INF_LOOP", line,
                        "do-while(true) loop lacks an exit condition (break, return, or throw).",
                        "Add a break or return statement inside the do-while body."));
            }
        }
    }

    /**
     * Checks if the loop has a valid exit statement.
     * Prevents false positives where an inner loop's break is mistakenly treated as an exit for the outer loop.
     */
    private boolean hasLoopExit(Node targetLoop, Statement body) {
        if (body == null) return false;

        // Returns or throws exit the entire method, hence exiting any loop
        if (!body.findAll(ReturnStmt.class).isEmpty()) return true;
        if (!body.findAll(ThrowStmt.class).isEmpty()) return true;

        // Check break statements: ensure the break belongs to this loop, not an inner loop!
        List<BreakStmt> breaks = body.findAll(BreakStmt.class);
        for (BreakStmt b : breaks) {
            Node enclosing = findImmediateEnclosingLoop(b);
            if (enclosing == targetLoop) {
                return true;
            }
        }

        return false;
    }

    private Node findImmediateEnclosingLoop(Node node) {
        Node curr = node.getParentNode().orElse(null);
        while (curr != null) {
            if (curr instanceof WhileStmt || curr instanceof ForStmt ||
                curr instanceof DoStmt || curr instanceof ForEachStmt) {
                return curr;
            }
            curr = curr.getParentNode().orElse(null);
        }
        return null;
    }

    private Set<String> extractVariableNames(BinaryExpr bin) {
        Set<String> names = new HashSet<>();
        for (NameExpr name : bin.findAll(NameExpr.class)) {
            names.add(name.getNameAsString());
        }
        return names;
    }

    private boolean isAnyVariableModified(Set<String> vars, Statement body) {
        if (body == null) return false;

        // Check unary operations: i++, ++i, i--, --i
        for (UnaryExpr u : body.findAll(UnaryExpr.class)) {
            if (u.getExpression() instanceof NameExpr &&
                    vars.contains(((NameExpr) u.getExpression()).getNameAsString())) {
                return true;
            }
        }

        // Check assignments: i = ..., i += ..., i -= ...
        for (AssignExpr a : body.findAll(AssignExpr.class)) {
            if (a.getTarget() instanceof NameExpr &&
                    vars.contains(((NameExpr) a.getTarget()).getNameAsString())) {
                return true;
            }
        }

        return false;
    }
}
