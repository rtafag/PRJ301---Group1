package com.group1.model.main;

import java.util.ArrayList;
import java.util.List;

public final class AnalysisOutcome {
    public boolean syntaxValid;
    public int classCount;
    public int methodCount;
    public float deduction;
    public String feedback;
    public final List<Finding> findings = new ArrayList<>();
}
