package com.group1.model;

import com.group1.model.RuleDefinition;

public final class Finding {
    public final RuleDefinition rule;
    public final int line;
    public final String suggestion;
    public final String file;

    public Finding(RuleDefinition rule, int line, String suggestion) {
        this(rule, line, suggestion, null);
    }

    public Finding(RuleDefinition rule, int line, String suggestion, String file) {
        this.rule = rule;
        this.line = line;
        this.suggestion = suggestion;
        this.file = file;
    }
}
