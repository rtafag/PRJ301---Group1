package com.group1.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AstSyntaxVisitor extends VoidVisitorAdapter<Void> {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final int FINGERPRINT_LENGTH = 16;

    private final AstModel.FileAst ast;
    private final Map<Node, AstModel.TypeInfo> typeIndex = new IdentityHashMap<>();

    public AstSyntaxVisitor(AstModel.FileAst ast) {
        this.ast = ast;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void visit(CompilationUnit unit, Void arg) {
        ast.packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        for (ImportDeclaration declaration : unit.getImports()) {
            ast.imports.add(importName(declaration));
        }
        List<TypeDeclaration> declared = unit.findAll(TypeDeclaration.class);
        for (TypeDeclaration type : declared) {
            AstModel.TypeInfo info = describeType(type);
            typeIndex.put(type, info);
            ast.types.add(info);
        }
        super.visit(unit, arg);
    }

    @Override
    public void visit(MethodDeclaration node, Void arg) {
        AstModel.MethodInfo info = describeCallable(node, node.getBody(), false);
        info.returnType = node.getType().asString();
        ast.methods.add(info);
        AstModel.TypeInfo owner = ownerType(node);
        if (owner != null) {
            addTypeReferences(owner, "return", info.returnType);
            addParameterReferences(owner, node.getParameters());
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(ConstructorDeclaration node, Void arg) {
        AstModel.MethodInfo info = describeCallable(node, Optional.of(node.getBody()), true);
        ast.methods.add(info);
        AstModel.TypeInfo owner = ownerType(node);
        if (owner != null) {
            addParameterReferences(owner, node.getParameters());
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(ForStmt node, Void arg) {
        addLoop(node, "for", node.getCompare().orElse(null), node.getUpdate(), node.getBody());
        super.visit(node, arg);
    }

    @Override
    public void visit(ForEachStmt node, Void arg) {
        addLoop(node, "foreach", node.getIterable(), new NodeList<Expression>(), node.getBody());
        super.visit(node, arg);
    }

    @Override
    public void visit(WhileStmt node, Void arg) {
        addLoop(node, "while", node.getCondition(), new NodeList<Expression>(), node.getBody());
        super.visit(node, arg);
    }

    @Override
    public void visit(DoStmt node, Void arg) {
        addLoop(node, "do", node.getCondition(), new NodeList<Expression>(), node.getBody());
        super.visit(node, arg);
    }

    @Override
    public void visit(ReturnStmt node, Void arg) {
        if (node.getExpression().isPresent() && node.getExpression().get() instanceof NullLiteralExpr) {
            ast.nullReturns.add(new AstModel.NullReturn(enclosingMethodName(node), line(node)));
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(ArrayCreationExpr node, Void arg) {
        if (insideLoop(node)) {
            StringBuilder type = new StringBuilder(node.getElementType().asString());
            for (int i = 0; i < node.getLevels().size(); i++) {
                type.append("[]");
            }
            ast.allocations.add(new AstModel.Allocation("array", type.toString(), enclosingMethodName(node),
                    line(node)));
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(ObjectCreationExpr node, Void arg) {
        String type = node.getType().asString();
        AstModel.TypeInfo owner = ownerType(node);
        if (owner != null) {
            addTypeReferences(owner, "creation", type);
        }
        if (insideLoop(node)) {
            ast.allocations.add(new AstModel.Allocation("object", type, enclosingMethodName(node), line(node)));
        }
        super.visit(node, arg);
    }

    private AstModel.TypeInfo describeType(TypeDeclaration<?> type) {
        AstModel.TypeInfo info = new AstModel.TypeInfo();
        info.name = type.getNameAsString();
        info.qualifiedName = qualifiedName(type);
        info.kind = kindOf(type);
        info.line = line(type);
        info.endLine = endLine(type);
        info.fieldCount = type.getFields().size();
        info.methodCount = type.getMethods().size();
        for (Modifier modifier : type.getModifiers()) {
            info.modifiers.add(modifier.getKeyword().asString());
        }
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration declaration = (ClassOrInterfaceDeclaration) type;
            declaration.getExtendedTypes().forEach(t -> {
                info.extendsTypes.add(t.asString());
                info.reference("extends", t.getNameAsString());
            });
            declaration.getImplementedTypes().forEach(t -> {
                info.implementsTypes.add(t.asString());
                info.reference("implements", t.getNameAsString());
            });
        } else if (type instanceof EnumDeclaration) {
            ((EnumDeclaration) type).getImplementedTypes().forEach(t -> {
                info.implementsTypes.add(t.asString());
                info.reference("implements", t.getNameAsString());
            });
        }
        for (FieldDeclaration field : type.getFields()) {
            addTypeReferences(info, "field", field.getCommonType().asString());
        }
        return info;
    }

    private AstModel.MethodInfo describeCallable(CallableDeclaration<?> node, Optional<BlockStmt> body,
            boolean constructor) {
        AstModel.MethodInfo info = new AstModel.MethodInfo();
        info.name = node.getNameAsString();
        info.constructor = constructor;
        info.line = line(node);
        info.endLine = endLine(node);
        info.lineCount = info.endLine - info.line + 1;
        info.hasBody = body.isPresent();
        info.hasJavadoc = node.getJavadocComment().isPresent();
        info.hasComment = node.getComment().isPresent();
        AstModel.TypeInfo owner = ownerType(node);
        info.owner = owner == null ? "" : owner.qualifiedName;
        StringBuilder signature = new StringBuilder(info.name).append('(');
        for (Parameter parameter : node.getParameters()) {
            String type = parameter.getType().asString();
            info.parameters.add(new AstModel.Param(parameter.getNameAsString(), type));
            if (signature.charAt(signature.length() - 1) != '(') {
                signature.append(',');
            }
            signature.append(type);
        }
        info.signature = signature.append(')').toString();
        for (Modifier modifier : node.getModifiers()) {
            info.modifiers.add(modifier.getKeyword().asString());
        }
        info.cyclomaticComplexity = cyclomatic(node);
        int maxDepth = 0;
        List<Node> loops = new ArrayList<>();
        node.walk(child -> {
            if (isLoop(child)) {
                loops.add(child);
            }
        });
        for (Node loop : loops) {
            maxDepth = Math.max(maxDepth, loopDepth(loop));
        }
        info.maxLoopDepth = maxDepth;
        if (body.isPresent()) {
            info.statementCount = Math.max(0, body.get().findAll(Statement.class).size() - 1);
            info.fingerprint = fingerprint(body.get());
        }
        return info;
    }

    private void addLoop(Node node, String kind, Expression condition, NodeList<Expression> updates,
            Statement body) {
        AstModel.LoopInfo loop = new AstModel.LoopInfo();
        loop.kind = kind;
        loop.line = line(node);
        loop.endLine = endLine(node);
        loop.method = enclosingMethodName(node);
        loop.depth = loopDepth(node);
        loop.condition = condition == null ? "" : oneLine(condition.toString());
        loop.hasExit = exits(body, false);
        if (!"foreach".equals(kind)) {
            loop.constantTrueCondition = condition == null || isTrue(condition);
            if (loop.constantTrueCondition) {
                loop.reason = "constant-true-condition";
            } else if (neverUpdated(node, condition, updates, body)) {
                loop.reason = "condition-never-updated";
            }
        }
        loop.infiniteCandidate = loop.reason != null && !loop.hasExit;
        ast.loops.add(loop);
    }

    private static boolean neverUpdated(Node loop, Expression condition, NodeList<Expression> updates,
            Statement body) {
        if (!condition.findAll(MethodCallExpr.class).isEmpty()
                || !condition.findAll(FieldAccessExpr.class).isEmpty()
                || !condition.findAll(ArrayAccessExpr.class).isEmpty()
                || !condition.findAll(ObjectCreationExpr.class).isEmpty()
                || !condition.findAll(ThisExpr.class).isEmpty()) {
            return false;
        }
        Set<String> used = new HashSet<>();
        for (NameExpr name : condition.findAll(NameExpr.class)) {
            used.add(name.getNameAsString());
        }
        if (used.isEmpty()) {
            return false;
        }
        Node callable = enclosingCallable(loop);
        if (callable == null) {
            return false;
        }
        Set<String> locals = new HashSet<>();
        for (Parameter parameter : callable.findAll(Parameter.class)) {
            locals.add(parameter.getNameAsString());
        }
        for (VariableDeclarator variable : callable.findAll(VariableDeclarator.class)) {
            locals.add(variable.getNameAsString());
        }
        if (!locals.containsAll(used)) {
            return false;
        }
        Set<String> assigned = new HashSet<>();
        collectAssigned(condition, assigned);
        collectAssigned(body, assigned);
        for (Expression update : updates) {
            collectAssigned(update, assigned);
        }
        for (String name : used) {
            if (assigned.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private static void collectAssigned(Node root, Set<String> assigned) {
        root.walk(node -> {
            if (node instanceof AssignExpr) {
                Expression target = ((AssignExpr) node).getTarget();
                if (target instanceof NameExpr) {
                    assigned.add(((NameExpr) target).getNameAsString());
                }
            } else if (node instanceof UnaryExpr) {
                UnaryExpr unary = (UnaryExpr) node;
                UnaryExpr.Operator operator = unary.getOperator();
                boolean mutating = operator == UnaryExpr.Operator.PREFIX_INCREMENT
                        || operator == UnaryExpr.Operator.PREFIX_DECREMENT
                        || operator == UnaryExpr.Operator.POSTFIX_INCREMENT
                        || operator == UnaryExpr.Operator.POSTFIX_DECREMENT;
                if (mutating && unary.getExpression() instanceof NameExpr) {
                    assigned.add(((NameExpr) unary.getExpression()).getNameAsString());
                }
            }
        });
    }

    private static boolean exits(Node node, boolean nested) {
        if (node instanceof ReturnStmt || node instanceof ThrowStmt) {
            return true;
        }
        if (node instanceof BreakStmt) {
            return ((BreakStmt) node).getLabel().isPresent() || !nested;
        }
        if (node instanceof MethodCallExpr && isSystemExit((MethodCallExpr) node)) {
            return true;
        }
        if (node instanceof LambdaExpr || node instanceof LocalClassDeclarationStmt) {
            return false;
        }
        if (node instanceof ObjectCreationExpr && ((ObjectCreationExpr) node).getAnonymousClassBody().isPresent()) {
            return false;
        }
        boolean childNested = nested || isLoop(node) || node instanceof SwitchStmt;
        for (Node child : node.getChildNodes()) {
            if (exits(child, childNested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSystemExit(MethodCallExpr call) {
        return "exit".equals(call.getNameAsString()) && call.getScope().isPresent()
                && "System".equals(call.getScope().get().toString());
    }

    private static boolean isTrue(Expression expression) {
        Expression current = expression;
        while (current instanceof EnclosedExpr) {
            current = ((EnclosedExpr) current).getInner();
        }
        return current instanceof BooleanLiteralExpr && ((BooleanLiteralExpr) current).getValue();
    }

    static boolean isLoop(Node node) {
        return node instanceof ForStmt || node instanceof ForEachStmt || node instanceof WhileStmt
                || node instanceof DoStmt;
    }

    private static boolean insideLoop(Node node) {
        Node parent = node.getParentNode().orElse(null);
        while (parent != null && !isBoundary(parent)) {
            if (isLoop(parent)) {
                return true;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return false;
    }

    private static int loopDepth(Node loop) {
        int depth = 1;
        Node parent = loop.getParentNode().orElse(null);
        while (parent != null && !isBoundary(parent)) {
            if (isLoop(parent)) {
                depth++;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return depth;
    }

    private static boolean isBoundary(Node node) {
        return node instanceof CallableDeclaration || node instanceof InitializerDeclaration
                || node instanceof TypeDeclaration;
    }

    private static Node enclosingCallable(Node node) {
        Node parent = node.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof CallableDeclaration) {
                return parent;
            }
            if (parent instanceof TypeDeclaration) {
                return null;
            }
            parent = parent.getParentNode().orElse(null);
        }
        return null;
    }

    private static String enclosingMethodName(Node node) {
        Node parent = node.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof CallableDeclaration) {
                return ((CallableDeclaration<?>) parent).getNameAsString();
            }
            if (parent instanceof InitializerDeclaration) {
                return ((InitializerDeclaration) parent).isStatic() ? "<clinit>" : "<init>";
            }
            if (parent instanceof TypeDeclaration) {
                return "<field>";
            }
            parent = parent.getParentNode().orElse(null);
        }
        return "";
    }

    private AstModel.TypeInfo ownerType(Node node) {
        Node parent = node.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof TypeDeclaration) {
                return typeIndex.get(parent);
            }
            parent = parent.getParentNode().orElse(null);
        }
        return null;
    }

    private String qualifiedName(TypeDeclaration<?> type) {
        StringBuilder name = new StringBuilder(type.getNameAsString());
        Node parent = type.getParentNode().orElse(null);
        while (parent != null) {
            if (parent instanceof TypeDeclaration) {
                name.insert(0, ((TypeDeclaration<?>) parent).getNameAsString() + ".");
            }
            parent = parent.getParentNode().orElse(null);
        }
        if (!ast.packageName.isEmpty()) {
            name.insert(0, ast.packageName + ".");
        }
        return name.toString();
    }

    private static String kindOf(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration) {
            return ((ClassOrInterfaceDeclaration) type).isInterface() ? "interface" : "class";
        }
        if (type instanceof EnumDeclaration) {
            return "enum";
        }
        if (type instanceof AnnotationDeclaration) {
            return "annotation";
        }
        return "record";
    }

    private static String importName(ImportDeclaration declaration) {
        String name = declaration.getNameAsString() + (declaration.isAsterisk() ? ".*" : "");
        return declaration.isStatic() ? "static " + name : name;
    }

    private static void addParameterReferences(AstModel.TypeInfo owner, NodeList<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            addTypeReferences(owner, "parameter", parameter.getType().asString());
        }
    }

    private static void addTypeReferences(AstModel.TypeInfo owner, String kind, String typeText) {
        Matcher matcher = IDENTIFIER.matcher(typeText);
        while (matcher.find()) {
            owner.reference(kind, matcher.group());
        }
    }

    private static int cyclomatic(Node root) {
        int[] total = { 1 };
        root.walk(node -> {
            if (node instanceof IfStmt || node instanceof ForStmt || node instanceof ForEachStmt
                    || node instanceof WhileStmt || node instanceof DoStmt || node instanceof CatchClause
                    || node instanceof ConditionalExpr) {
                total[0]++;
            } else if (node instanceof SwitchEntry && !((SwitchEntry) node).getLabels().isEmpty()) {
                total[0]++;
            } else if (node instanceof BinaryExpr) {
                BinaryExpr.Operator operator = ((BinaryExpr) node).getOperator();
                if (operator == BinaryExpr.Operator.AND || operator == BinaryExpr.Operator.OR) {
                    total[0]++;
                }
            }
        });
        return total[0];
    }

    private static String fingerprint(Node root) {
        StringBuilder shape = new StringBuilder();
        root.walk(Node.TreeTraversal.PREORDER, node -> {
            if (node instanceof com.github.javaparser.ast.comments.Comment) {
                return;
            }
            shape.append(node.getClass().getSimpleName());
            if (node instanceof com.github.javaparser.ast.expr.SimpleName) {
                shape.append(':').append(((com.github.javaparser.ast.expr.SimpleName) node).getIdentifier());
            } else if (node instanceof com.github.javaparser.ast.expr.LiteralStringValueExpr) {
                shape.append(':').append(((com.github.javaparser.ast.expr.LiteralStringValueExpr) node).getValue());
            } else if (node instanceof BooleanLiteralExpr) {
                shape.append(':').append(((BooleanLiteralExpr) node).getValue());
            }
            shape.append(';');
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(shape.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String oneLine(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 120 ? flat.substring(0, 117) + "..." : flat;
    }

    private static int line(Node node) {
        return node.getRange().isPresent() ? node.getRange().get().begin.line : 0;
    }

    private static int endLine(Node node) {
        return node.getRange().isPresent() ? node.getRange().get().end.line : 0;
    }
}
