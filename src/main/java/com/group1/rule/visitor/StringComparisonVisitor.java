package com.group1.rule.visitor;

import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class StringComparisonVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(BinaryExpr n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        // Check for == or !=
        if (n.getOperator() == BinaryExpr.Operator.EQUALS || n.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
            boolean leftIsString = n.getLeft() instanceof StringLiteralExpr;
            boolean rightIsString = n.getRight() instanceof StringLiteralExpr;

            if (leftIsString || rightIsString) {
                int line = n.getBegin().map(p -> p.line).orElse(1);
                String op = n.getOperator() == BinaryExpr.Operator.EQUALS ? "==" : "!=";
                arg.add(new RuleViolationResult("STR_EQ", line,
                        "String compared using reference equality operator ('" + op + "') instead of .equals().",
                        "Replace '" + op + "' with .equals() or Objects.equals(str1, str2) to compare content."));
            }
        }
    }
}
