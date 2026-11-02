package com.group1.rule.visitor;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class AssignmentInConditionVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(IfStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkCondition(n.getCondition(), arg);
    }

    @Override
    public void visit(WhileStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkCondition(n.getCondition(), arg);
    }

    @Override
    public void visit(DoStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);
        checkCondition(n.getCondition(), arg);
    }

    private void checkCondition(Expression condition, List<RuleViolationResult> arg) {
        if (condition == null) return;
        List<AssignExpr> assignments = condition.findAll(AssignExpr.class);
        for (AssignExpr a : assignments) {
            int line = a.getBegin().map(p -> p.line).orElse(1);
            arg.add(new RuleViolationResult("ASSIGN_IN_COND", line,
                    "Assignment operator ('=') used inside conditional expression instead of comparison ('==').",
                    "Replace '=' with '==' or move assignment outside the conditional expression."));
        }
    }
}
