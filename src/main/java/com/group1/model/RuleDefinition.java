package com.group1.model;
public class RuleDefinition {
    public int ruleId; public String ruleCode; public String ruleName;
    public String severity; public float penaltyPoints; public String description;
    public RuleDefinition(int ruleId, String ruleCode, String ruleName, String severity, float penaltyPoints, String description) {
        this.ruleId=ruleId; this.ruleCode=ruleCode; this.ruleName=ruleName;
        this.severity=severity; this.penaltyPoints=penaltyPoints; this.description=description;
    }
}
