// Shared axe-core helper for the accessibility regression checks (issue #529).
import { appendFileSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// WCAG 2.1 A and AA rules, same tags as the manual audit (docs/ACCESSIBILITY.md).
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

// Violations with these impacts fail the job; anything else is only reported.
const BLOCKING_IMPACTS = new Set(['critical', 'serious']);

// Single allow-list of known, accepted `incomplete` results (needs manual review).
// Anything else that axe cannot decide is reported as a warning, never silently.
//  - video-caption: lessons have no <track> yet; tracked in
//    https://github.com/urrestarazu-org/menta-dance/issues/568
const ALLOWED_INCOMPLETE = new Set(['video-caption']);

function describe(items) {
  return items.map((v) => `${v.id} [${v.impact}] x${v.nodes.length}: ${v.help}`);
}

function writeSummary(name, url, warnings) {
  const summaryFile = process.env.GITHUB_STEP_SUMMARY;
  if (!summaryFile || warnings.length === 0) {
    return;
  }
  const lines = [`#### ${name} (\`${url}\`)`, ...warnings.map((w) => `- ${w}`), ''];
  appendFileSync(summaryFile, `${lines.join('\n')}\n`);
}

/**
 * Runs axe on the current page. Fails on critical/serious violations; reports
 * moderate/minor violations and unexpected incomplete results as warnings.
 */
export async function expectNoBlockingViolations(page, name) {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();

  const blocking = results.violations.filter((v) => BLOCKING_IMPACTS.has(v.impact));
  const minor = results.violations.filter((v) => !BLOCKING_IMPACTS.has(v.impact));
  const unexpectedIncomplete = results.incomplete.filter((i) => !ALLOWED_INCOMPLETE.has(i.id));

  const warnings = [
    ...describe(minor).map((m) => `violation (non-blocking) ${m}`),
    ...describe(unexpectedIncomplete).map((m) => `needs manual review ${m}`),
  ];
  for (const warning of warnings) {
    test.info().annotations.push({ type: 'a11y-warning', description: `${name}: ${warning}` });
    // GitHub Actions workflow command: shows up as an annotation on the run.
    console.log(`::warning title=a11y ${name}::${warning}`);
  }
  writeSummary(name, page.url(), warnings);

  const details = blocking.map((v) => ({
    rule: v.id,
    impact: v.impact,
    help: v.help,
    helpUrl: v.helpUrl,
    targets: v.nodes.map((n) => n.target.join(' ')),
  }));
  expect(details, `${name}: critical/serious accessibility violations`).toEqual([]);
}
