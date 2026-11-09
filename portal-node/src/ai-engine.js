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

function addFinding(findings, rule, source, lineNumber, suggestedFix) {
  if (!rule) return;
  findings.push({
    ruleCode: rule.ruleCode,
    ruleName: rule.ruleName,
    severity: rule.severity,
    penaltyPoints: Number(rule.penaltyPoints) || 0,
    lineNumber,
    suggestedFix
  });
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
    },
    {
      code: 'COMMENT',
      pattern: /\b(?:public|protected|private)\s+(?:static\s+)?(?:void|[A-Z_a-z][\w<>.?\[\]]*)\s+[A-Za-z_$][\w$]*\s*\([^)]*\)\s*\{/g,
      suggestion: 'Add a concise comment or Javadoc for this method.'
    },
    {
      code: 'LONG_METHOD',
      pattern: /\b(?:public|protected|private)\s+(?:static\s+)?(?:void|[A-Z_a-z][\w<>.?\[\]]*)\s+[A-Za-z_$][\w$]*\s*\([^)]*\)\s*\{/g,
      suggestion: 'Split this method into smaller, focused methods.'
    },
    {
      code: 'DUPLICATE_CODE',
      pattern: /\b(?:public|protected|private)\s+(?:static\s+)?(?:void|[A-Z_a-z][\w<>.?\[\]]*)\s+[A-Za-z_$][\w$]*\s*\([^)]*\)\s*\{/g,
      suggestion: 'Extract repeated logic into a shared method.'
    }
  ];

  for (const check of patterns) {
    const rule = findRule(rules, check.code);
    if (!rule) continue;
    for (const match of source.matchAll(check.pattern)) {
      if (check.code === 'CS' && !/^[a-z]/.test(match[1])) continue;
      if (check.code === 'COMMENT') {
        const before = source.slice(0, match.index);
        const prefix = before.trimEnd();
        if (/(?:\/\*\*?[\s\S]*?\*\/|\/\/[^\n]*)\s*$/.test(prefix) || /@Override\s*$/.test(prefix)) continue;
      }
      if (check.code === 'LONG_METHOD' || check.code === 'DUPLICATE_CODE') continue;
      addFinding(findings, rule, source, lineAt(source, match.index), check.suggestion);
    }
  }

  const scrub = (value) => value.replace(/[^\n]/g, ' ');
  const scrubbed = source.replace(/\/\*[\s\S]*?\*\//g, scrub)
    .replace(/\/\/.*$/gm, scrub)
    .replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g, scrub);
  const methodPattern = /\b(?:public|protected|private)\s+(?:static\s+)?(?:void|[A-Z_a-z][\w<>.?\[\]]*)\s+[A-Za-z_$][\w$]*\s*\([^)]*\)\s*\{/g;
  const methodRanges = [];
  for (const match of scrubbed.matchAll(methodPattern)) {
    const openBrace = scrubbed.indexOf('{', match.index);
    let depth = 0;
    let end = openBrace;
    for (; end < scrubbed.length; end += 1) {
      if (scrubbed[end] === '{') depth += 1;
      if (scrubbed[end] === '}' && --depth === 0) break;
    }
    if (depth !== 0) continue;
    const body = scrubbed.slice(openBrace + 1, end);
    const lineCount = lineAt(scrubbed, end) - lineAt(scrubbed, openBrace);
    const name = match[0].match(/[A-Za-z_$][\w$]*\s*\([^)]*\)\s*\{$/)?.[0] ?? match[0];
    methodRanges.push({ start: match.index, end, body, lineCount, name });
    const longRule = findRule(rules, 'LONG_METHOD');
    if (longRule && lineCount > 40) {
      addFinding(findings, longRule, source, lineAt(source, match.index), `Method spans ${lineCount} lines; ${longRule.description || 'split it into smaller methods.'}`);
    }
  }

  const duplicateRule = findRule(rules, 'DUPLICATE_CODE');
  if (duplicateRule) {
    for (let first = 0; first < methodRanges.length; first += 1) {
      const normalizedFirst = methodRanges[first].body.replace(/\s+/g, ' ').trim();
      if (normalizedFirst.length < 80) continue;
      for (let second = first + 1; second < methodRanges.length; second += 1) {
        const normalizedSecond = methodRanges[second].body.replace(/\s+/g, ' ').trim();
        if (normalizedFirst === normalizedSecond) {
          addFinding(findings, duplicateRule, source, lineAt(source, methodRanges[second].start), 'This method body duplicates another method; extract the shared logic.');
        }
      }
    }
  }
  return findings;
}

export function extractSlicedContext(source, findings) {
  if (!source || !findings || findings.length === 0) return '';
  const lines = source.split('\n');
  return findings.map((f) => {
    const errLine = f.lineNumber || 1;
    const start = Math.max(0, errLine - 3);
    const end = Math.min(lines.length - 1, errLine + 2);
    const snippet = [];
    for (let l = start; l <= end; l++) {
      const prefix = (l + 1 === errLine) ? '>> ' : '   ';
      snippet.push(`${prefix}${l + 1}: ${lines[l]}`);
    }
    return `--- Ngữ cảnh Dòng ${errLine} (Rule: ${f.ruleCode}) ---\n${snippet.join('\n')}`;
  }).join('\n\n');
}

export function buildPromptPayload(source, findings) {
  const slicedContext = extractSlicedContext(source, findings);
  const violationsList = findings.map((f, i) =>
    `${i + 1}. [${f.ruleCode}] ${f.ruleName || f.ruleCode} (Dòng ${f.lineNumber}): ${f.suggestedFix}`
  ).join('\n');

  return `Danh sách vi phạm do AST Rule Engine phát hiện (${findings.length} lỗi):\n${violationsList}\n\nTrích đoạn mã nguồn ngữ cảnh xung quanh dòng bị lỗi (Context Slicing):\n${slicedContext}`;
}

const AI_SYSTEM_PROMPT = `You are an expert Java Teaching Assistant in the AITA system for PRJ301.
Your objective is to provide pedagogical, constructive code review in GitHub-flavored Markdown.
STRICT ANTI-HALLUCINATION CONSTRAINTS:
1. Base review ONLY on the exact AST violations and sliced lines provided. NEVER invent imaginary errors.
2. Do NOT change, assign, or recalculate numeric grades.
3. For EACH violation, structure feedback with:
   - Root Cause & Runtime Risk
   - Recommended Fix & Code Snippet (Before/After)
   - Best Practice Tip (PRJ301 / Effective Java)
4. Respond in Vietnamese.`;

async function requestExternalFeedback(source, findings) {
  if (!findings || findings.length === 0) return '';
  const provider = (process.env.AI_PROVIDER ?? 'disabled').toLowerCase();
  const userPrompt = buildPromptPayload(source, findings);

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
          { role: 'system', content: AI_SYSTEM_PROMPT },
          { role: 'user', content: userPrompt }
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
        systemInstruction: { parts: [{ text: AI_SYSTEM_PROMPT }] },
        contents: [{ parts: [{ text: userPrompt }] }]
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