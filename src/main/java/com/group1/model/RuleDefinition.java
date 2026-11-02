package com.group1.model;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "RuleDefinition")
public class RuleDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "rule_id", nullable = false)
    public int ruleId;

    @Column(name = "rule_code", length = 50)
    public String ruleCode;

    @Column(name = "rule_name", length = 200)
    public String ruleName;

    @Column(name = "severity", length = 50)
    public String severity;

    @Column(name = "penalty_points")
    public float penaltyPoints;

    @Column(name = "description")
    public String description;

    public RuleDefinition() {
    }

    public RuleDefinition(int ruleId, String ruleCode, String ruleName, String severity, float penaltyPoints, String description) {
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.severity = severity;
        this.penaltyPoints = penaltyPoints;
        this.description = description;
    }

    public int getRuleId() {
        return ruleId;
    }

    public void setRuleId(int ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public float getPenaltyPoints() {
        return penaltyPoints;
    }

    public void setPenaltyPoints(float penaltyPoints) {
        this.penaltyPoints = penaltyPoints;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "RuleDefinition{" +
                "ruleId=" + ruleId +
                ", ruleCode='" + ruleCode + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", severity='" + severity + '\'' +
                ", penaltyPoints=" + penaltyPoints +
                '}';
    }
}
