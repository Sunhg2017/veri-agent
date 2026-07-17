#!/usr/bin/env node
/**
 * CSS 使用审计：提取 styles.css 中定义的 class 选择器，
 * 与 src 下 tsx/ts 文件中的 className 引用对比，输出未使用清单。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';

const root = new URL('..', import.meta.url).pathname;
const cssPath = join(root, 'src/styles.css');
const css = readFileSync(cssPath, 'utf8');

// 1. 提取 CSS 中所有 class 选择器（.xxx），排除 keyframes 内百分比与注释
const stripped = css
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/@keyframes[\s\S]*?\{[\s\S]*?\}\s*\}/g, '');
const classDefs = new Set();
for (const match of stripped.matchAll(/\.([a-zA-Z][a-zA-Z0-9_-]*)/g)) {
  classDefs.add(match[1]);
}

// 2. 收集 src 下所有 tsx/ts 文件内容
function walk(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    const stat = statSync(full);
    if (stat.isDirectory()) walk(full, acc);
    else if (['.tsx', '.ts', '.jsx', '.js'].includes(extname(entry))) acc.push(full);
  }
  return acc;
}
const files = walk(join(root, 'src'));
const source = files.map((f) => readFileSync(f, 'utf8')).join('\n');

// 3. 判断每个 class 是否被引用（子串匹配，覆盖模板字符串拼接的前缀场景）
const unused = [];
const used = [];
for (const cls of classDefs) {
  // 精确 class 名或作为前缀（如 btn-${x}）出现
  if (source.includes(cls)) {
    used.push(cls);
  } else {
    unused.push(cls);
  }
}

console.log(`CSS 定义 class 数: ${classDefs.size}`);
console.log(`被引用: ${used.length}`);
console.log(`未引用: ${unused.length}`);
console.log('\n--- 未引用 class 清单 ---');
for (const cls of unused.sort()) console.log(cls);
