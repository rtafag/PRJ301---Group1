package com.group1.rule.visitor;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.List;

public class DeadCodeVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    @Override
    public void visit(BlockStmt n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        List<Statement> stmts = n.getStatements();
        boolean jumpFound = false;
        String jumpType = "";

        for (int i = 0; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (jumpFound) {
                int line = s.getBegin().map(p -> p.line).orElse(1);
                arg.add(new RuleViolationResult("DEAD_CODE", line,
                        "Unreachable code detected following an unconditional '" + jumpType + "' statement.",
                        "Remove or relocate unreachable code after '" + jumpType + "'."));
                break; // Only flag the first unreachable statement per block
            }

            if (s instanceof ReturnStmt) {
                jumpFound = true;
                jumpType = "return";
            } else if (s instanceof ThrowStmt) {
                jumpFound = true;
                jumpType = "throw";
            } else if (s instanceof BreakStmt) {
                jumpFound = true;
                jumpType = "break";
            } else if (s instanceof ContinueStmt) {
                jumpFound = true;
                jumpType = "continue";
            }
        }
    }
}
