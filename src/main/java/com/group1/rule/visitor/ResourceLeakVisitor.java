package com.group1.rule.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.rule.RuleViolationResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResourceLeakVisitor extends VoidVisitorAdapter<List<RuleViolationResult>> {

    private static final Set<String> AUTO_CLOSEABLES = new HashSet<>(Arrays.asList(
            "Connection", "PreparedStatement", "Statement", "CallableStatement", "ResultSet",
            "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
            "BufferedReader", "BufferedWriter", "Scanner", "InputStream", "OutputStream"
    ));

    @Override
    public void visit(VariableDeclarationExpr n, List<RuleViolationResult> arg) {
        super.visit(n, arg);

        // Check if inside try-with-resources
        Node parent = n.getParentNode().orElse(null);
        if (parent instanceof TryStmt) {
            TryStmt tryStmt = (TryStmt) parent;
            if (tryStmt.getResources().contains(n)) {
                // Properly managed by try-with-resources!
                return;
            }
        }

        for (VariableDeclarator var : n.getVariables()) {
            String typeName = var.getType().asString();
            if (AUTO_CLOSEABLES.contains(typeName)) {
                int line = var.getBegin().map(p -> p.line).orElse(1);
                String varName = var.getNameAsString();

                // Check if variable is closed in the enclosing method
                MethodDeclaration enclosingMethod = findEnclosingMethod(n);
                boolean hasClose = false;
                if (enclosingMethod != null) {
                    for (MethodCallExpr call : enclosingMethod.findAll(MethodCallExpr.class)) {
                        if ("close".equals(call.getNameAsString()) &&
                                call.getScope().isPresent() &&
                                call.getScope().get().toString().equals(varName)) {
                            hasClose = true;
                            break;
                        }
                    }
                }

                if (!hasClose) {
                    arg.add(new RuleViolationResult("RES_LEAK", line,
                            "Resource '" + varName + "' (" + typeName + ") is not managed via try-with-resources or closed.",
                            "Use try-with-resources: try (" + typeName + " " + varName + " = ...) { ... } to prevent leaks."));
                }
            }
        }
    }

    private MethodDeclaration findEnclosingMethod(Node node) {
        Node curr = node;
        while (curr != null) {
            if (curr instanceof MethodDeclaration) {
                return (MethodDeclaration) curr;
            }
            curr = curr.getParentNode().orElse(null);
        }
        return null;
    }
}
