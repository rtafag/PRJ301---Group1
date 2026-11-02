package com.group1.rule.visitor;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class DivisionByZeroVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(BinaryExpr n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        if (n.getOperator() == BinaryExpr.Operator.DIVIDE || n.getOperator() == BinaryExpr.Operator.REMAINDER) {
            boolean isZero = false;
            if (n.getRight() instanceof IntegerLiteralExpr) {
                IntegerLiteralExpr lit = (IntegerLiteralExpr) n.getRight();
                if ("0".equals(lit.getValue())) isZero = true;
            } else if (n.getRight() instanceof LongLiteralExpr) {
                LongLiteralExpr lit = (LongLiteralExpr) n.getRight();
                if ("0".equals(lit.getValue()) || "0L".equalsIgnoreCase(lit.getValue())) isZero = true;
            }

            if (isZero) {
                int line = n.getBegin().map(p -> p.line).orElse(1);
                String op = n.getOperator() == BinaryExpr.Operator.DIVIDE ? "/" : "%";
                arg.add(new RuleViolationResult("DIV_ZERO", line,
                        "Division or modulo by literal zero ('" + op + " 0') triggers fatal ArithmeticException.",
                        "Add a guard check to ensure divisor is non-zero before performing division."));
            }
        }
    }
}
