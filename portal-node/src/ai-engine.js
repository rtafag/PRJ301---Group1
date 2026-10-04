import { createHash } from 'node:crypto';
import { parse } from 'java-parser';

function summarizeSyntaxTree(root) {
  const stack = [root];
  const visited = new Set();
  const productions = new Set();
  let nodeCount = 0;

  while (stack.length && nodeCount < 20000) {
    const node = stack.pop();
    if (!node || typeof node !== 'object' || visited.has(node)) continue;
    visited.add(node);
    if (Array.isArray(node)) {
      stack.push(...node);
      continue;
    }
    if (typeof node.name === 'string') {
      productions.add(node.name);
      nodeCount += 1;
    }
    if (node.children && typeof node.children === 'object') {
      stack.push(...Object.values(node.children));
    }
  }

  return {
    root: root.name,
    grammarNodeCount: nodeCount,
    productionCount: productions.size
  };
}

function lineAt(source, index) {
  return source.slice(0, index).split('\n').length;
}

function findRule(rules, code) {
  return rules.find((rule) => rule.ruleCode === code && rule.enabled !== false);
}

function runStaticRules(source, rules) {
  const findings = [];
  const patterns = [
    {
      code: 'NPE',
      pattern: /\breturn\s+null\s*;/g,
      suggestion: 'Avoid returning null without documenting or checking the result.'
    },
    {
      code: 'OOM',
      pattern: /\b(?:while|for)\s*\([^)]*\)[^{]*\{[^}]{0,600}\bnew\s+(?:byte|char|int|long)\s*\[/gs,
      suggestion: 'Review allocations inside loops; repeated allocation can exhaust memory.'
    },
    {
      code: 'CS',
      pattern: /\bclass\s+([a-z][A-Za-z0-9_]*)/g,
      suggestion: 'Use an uppercase initial letter for Java class names.'
    }
  ];

  for (const check of patterns) {
    const rule = findRule(rules, check.code);
    if (!rule) continue;
    for (const match of source.matchAll(check.pattern)) {
      if (check.code === 'CS' && !/^[a-z]/.test(match[1])) continue;
      findings.push({
        ruleCode: rule.ruleCode,
        ruleName: rule.ruleName,
        severity: rule.severity,
        penaltyPoints: Number(rule.penaltyPoints) || 0,
        lineNumber: lineAt(source, match.index),
        suggestedFix: check.suggestion
      });
    }
  }
  return findings;
}

async function requestExternalFeedback(source, findings) {
  const provider = (process.env.AI_PROVIDER ?? 'disabled').toLowerCase();
  if (provider === 'openai' && process.env.OPENAI_API_KEY) {
    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${process.env.OPENAI_API_KEY}`,
        'Content-Type': 'application/json'
      },
      signal: AbortSignal.timeout(15000),
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || 'gpt-4o-mini',
        messages: [
          { role: 'system', content: 'Review Java source for learning. Explain issues briefly. Do not assign or change the numeric grade.' },
          { role: 'user', content: JSON.stringify({ findings, source }) }
        ],
        temperature: 0.2
      })
    });
    if (!response.ok) throw new Error(`OpenAI request failed with status ${response.status}.`);
    const payload = await response.json();
    return payload.choices?.[0]?.message?.content?.trim() || '';
  }

  if (provider === 'gemini' && process.env.GEMINI_API_KEY) {
    const model = process.env.GEMINI_MODEL || 'gemini-2.0-flash';
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(process.env.GEMINI_API_KEY)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(15000),
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: 'Review Java source for learning. Explain issues briefly. Do not assign or change the numeric grade.' }] },
        contents: [{ parts: [{ text: JSON.stringify({ findings, source }) }] }]
      })
    });
    if (!response.ok) throw new Error(`Gemini request failed with status ${response.status}.`);
    const payload = await response.json();
    return payload.candidates?.[0]?.content?.parts?.map((part) => part.text).join('\n').trim() || '';
  }

  return '';
}

export function sha256(source) {
  return createHash('sha256').update(source, 'utf8').digest('hex');
}

export async function analyzeJavaSource(source, rules) {
  let syntaxTree;
  try {
    syntaxTree = parse(source);
  } catch (error) {
    return {
      syntaxValid: false,
      astSummary: null,
      violations: [],
      deduction: 0,
      totalScore: 0,
      aiFeedback: `Java syntax could not be parsed: ${String(error.message).split('\n')[0]}`
    };
  }

  const violations = runStaticRules(source, rules);
  const deduction = Math.min(10, violations.reduce((sum, item) => sum + item.penaltyPoints, 0));
  let aiFeedback = violations.length
    ? 'Static analysis found configured rule violations. Review the findings below.'
    : 'Static analysis found no configured rule violations.';
  let providerWarning = null;

  try {
    const externalFeedback = await requestExternalFeedback(source, violations);
    if (externalFeedback) aiFeedback = externalFeedback;
  } catch (error) {
    providerWarning = error.message;
  }

  return {
    syntaxValid: true,
    astSummary: summarizeSyntaxTree(syntaxTree),
    violations,
    deduction,
    totalScore: Math.max(0, 10 - deduction),
    aiFeedback,
    providerWarning
  };
}