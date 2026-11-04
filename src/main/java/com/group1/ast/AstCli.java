package com.group1.ast;

import java.io.IOException;
import java.nio.file.Paths;

public final class AstCli {
    private AstCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: AstCli <file.java|directory|archive.zip> [--pretty] [--no-tree]");
            System.exit(2);
        }
        boolean pretty = false;
        boolean tree = true;
        for (int i = 1; i < args.length; i++) {
            if ("--pretty".equals(args[i])) {
                pretty = true;
            } else if ("--no-tree".equals(args[i])) {
                tree = false;
            }
        }
        AstModel.ProjectAst project = new AstProjectAnalyzer().analyze(Paths.get(args[0]));
        System.out.println(AstJsonSerializer.toJson(project, tree, pretty));
    }
}
