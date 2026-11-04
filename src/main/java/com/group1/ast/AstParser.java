package com.group1.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class AstParser {
    public static final int MAX_TREE_DEPTH = 8;
    public static final int MAX_TREE_NODES = 300;

    private final JavaParser parser;

    public AstParser() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(languageLevel());
        this.parser = new JavaParser(configuration);
    }

    public AstModel.FileAst parse(AstModel.SourceFile file) {
        AstModel.FileAst ast = new AstModel.FileAst(file.path);
        ast.lines = countLines(file.content);
        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(file.content);
        } catch (RuntimeException exception) {
            ast.syntaxValid = false;
            ast.errors.add(new AstModel.SyntaxIssue(1, 1, "Parser failure: " + exception.getMessage()));
            return ast;
        }
        for (Problem problem : result.getProblems()) {
            ast.errors.add(toIssue(problem));
        }
        ast.syntaxValid = result.isSuccessful();
        Optional<CompilationUnit> unit = result.getResult();
        if (unit.isPresent()) {
            ast.attach(unit.get());
            ast.commentCount = unit.get().getAllComments().size();
            new AstSyntaxVisitor(ast).visit(unit.get(), null);
            measure(ast, unit.get());
            buildTree(ast, unit.get());
        }
        return ast;
    }

    private static ParserConfiguration.LanguageLevel languageLevel() {
        try {
            return ParserConfiguration.LanguageLevel.valueOf("JAVA_17");
        } catch (IllegalArgumentException exception) {
            return ParserConfiguration.LanguageLevel.BLEEDING_EDGE;
        }
    }

    private static AstModel.SyntaxIssue toIssue(Problem problem) {
        int line = 1;
        int column = 1;
        Optional<TokenRange> location = problem.getLocation();
        if (location.isPresent()) {
            Optional<Range> range = location.get().getBegin().getRange();
            if (range.isPresent()) {
                line = range.get().begin.line;
                column = range.get().begin.column;
            }
        }
        String message = problem.getMessage();
        String first = message == null ? "Syntax error" : message.split("\\R", 2)[0].trim();
        return new AstModel.SyntaxIssue(line, column, first.length() > 300 ? first.substring(0, 300) : first);
    }

    private static int countLines(String content) {
        if (content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return content.endsWith("\n") ? lines - 1 : lines;
    }

    private static void measure(AstModel.FileAst ast, Node root) {
        Deque<Node> nodes = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        nodes.push(root);
        depths.push(1);
        int count = 0;
        int deepest = 0;
        while (!nodes.isEmpty()) {
            Node node = nodes.pop();
            int depth = depths.pop();
            if (node instanceof Comment) {
                continue;
            }
            count++;
            deepest = Math.max(deepest, depth);
            for (Node child : node.getChildNodes()) {
                nodes.push(child);
                depths.push(depth + 1);
            }
        }
        ast.nodeCount = count;
        ast.maxDepth = deepest;
    }

    private static void buildTree(AstModel.FileAst ast, CompilationUnit unit) {
        AstModel.TreeNode root = new AstModel.TreeNode("CompilationUnit", ast.path, 1);
        int[] budget = { MAX_TREE_NODES };
        boolean[] truncated = { false };
        attachChildren(unit, root, 1, budget, truncated);
        ast.tree = root;
        ast.treeTruncated = truncated[0];
    }

    private static void attachChildren(Node parent, AstModel.TreeNode target, int depth, int[] budget,
            boolean[] truncated) {
        for (Node child : parent.getChildNodes()) {
            if (child instanceof Comment) {
                continue;
            }
            if (isStructural(child)) {
                if (depth >= MAX_TREE_DEPTH || budget[0] <= 0) {
                    truncated[0] = true;
                    continue;
                }
                budget[0]--;
                AstModel.TreeNode node = new AstModel.TreeNode(child.getClass().getSimpleName(), labelOf(child),
                        child.getRange().isPresent() ? child.getRange().get().begin.line : 0);
                target.children.add(node);
                attachChildren(child, node, depth + 1, budget, truncated);
            } else if (descendable(child)) {
                attachChildren(child, target, depth, budget, truncated);
            }
        }
    }

    private static boolean isStructural(Node node) {
        return node instanceof TypeDeclaration || node instanceof MethodDeclaration
                || node instanceof ConstructorDeclaration || node instanceof InitializerDeclaration
                || node instanceof FieldDeclaration || node instanceof IfStmt || node instanceof ForStmt
                || node instanceof ForEachStmt || node instanceof WhileStmt || node instanceof DoStmt
                || node instanceof SwitchStmt || node instanceof TryStmt || node instanceof CatchClause
                || node instanceof SynchronizedStmt || node instanceof ReturnStmt || node instanceof ThrowStmt
                || node instanceof LambdaExpr;
    }

    private static boolean descendable(Node node) {
        if (node instanceof Expression) {
            return node instanceof MethodCallExpr || node instanceof ObjectCreationExpr
                    || node instanceof LambdaExpr;
        }
        return true;
    }

    private static String labelOf(Node node) {
        String label;
        if (node instanceof TypeDeclaration) {
            label = ((TypeDeclaration<?>) node).getNameAsString();
        } else if (node instanceof MethodDeclaration) {
            label = ((MethodDeclaration) node).getNameAsString();
        } else if (node instanceof ConstructorDeclaration) {
            label = ((ConstructorDeclaration) node).getNameAsString();
        } else if (node instanceof FieldDeclaration) {
            StringBuilder names = new StringBuilder();
            ((FieldDeclaration) node).getVariables().forEach(v -> {
                if (names.length() > 0) {
                    names.append(',');
                }
                names.append(v.getNameAsString());
            });
            label = names.toString();
        } else if (node instanceof IfStmt) {
            label = ((IfStmt) node).getCondition().toString();
        } else if (node instanceof WhileStmt) {
            label = ((WhileStmt) node).getCondition().toString();
        } else if (node instanceof DoStmt) {
            label = ((DoStmt) node).getCondition().toString();
        } else if (node instanceof ForEachStmt) {
            label = ((ForEachStmt) node).getIterable().toString();
        } else if (node instanceof ForStmt) {
            label = ((ForStmt) node).getCompare().map(Object::toString).orElse("");
        } else if (node instanceof SwitchStmt) {
            label = ((SwitchStmt) node).getSelector().toString();
        } else if (node instanceof ReturnStmt) {
            label = ((ReturnStmt) node).getExpression().map(Object::toString).orElse("");
        } else {
            label = "";
        }
        String flat = label.replaceAll("\\s+", " ").trim();
        return flat.length() > 60 ? flat.substring(0, 57) + "..." : flat;
    }
}
