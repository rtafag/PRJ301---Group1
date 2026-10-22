package com.group1.model;

import com.group1.model.RuleDefinition;

public final class Finding {
    public final RuleDefinition rule;
    public final int line;
    public final String suggestion;

    public Finding(RuleDefinition rule, int line, String suggestion) {
        this.rule = rule;
        this.line = line;
        this.suggestion = suggestion;
    }
}
