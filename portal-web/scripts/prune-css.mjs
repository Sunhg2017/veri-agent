#!/usr/bin/env node
/**
 * CSS 清理：删除 styles.css 中未被 src 引用的 class 规则。
 * - 保留 ant-*（antd 运行时 class 定制）
 * - 选择器列表中仅移除完全未引用的选择器；列表清空则删除整个规则块
 * - 支持 @media 等嵌套块；@keyframes 原样保留
 * 输出: src/styles.css（原地重写，先备份到 styles.css.bak）
 */
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';

const root = new URL('..', import.meta.url).pathname;
const cssPath = join(root, 'src/styles.css');
const css = readFileSync(cssPath, 'utf8');

/* ---- 收集源码引用 ---- */
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
const source = walk(join(root, 'src')).map((f) => readFileSync(f, 'utf8')).join('\n');
const e2eSource = (() => {
  try {
    return walk(join(root, 'e2e')).map((f) => readFileSync(f, 'utf8')).join('\n');
  } catch {
    return '';
  }
})();
const referenced = source + '\n' + e2eSource;

function selectorIsUnused(selector) {
  const classes = [...selector.matchAll(/\.([a-zA-Z][a-zA-Z0-9_-]*)/g)].map((m) => m[1]);
  if (classes.length === 0) return false; // 元素/伪类选择器保留
  // ant-* 运行时 class 一律视为使用中
  return classes.every((cls) => !cls.startsWith('ant-') && !referenced.includes(cls));
}

/* ---- 简易 CSS 解析（支持嵌套块） ---- */
function parseBlocks(text) {
  // 返回 tokens: { type: 'rule'|'at-block'|'raw', selector, body, children, name }
  const tokens = [];
  let i = 0;
  let raw = '';
  while (i < text.length) {
    const open = text.indexOf('{', i);
    if (open === -1) {
      raw += text.slice(i);
      break;
    }
    const prelude = text.slice(i, open).trim();
    const beforePrelude = text.slice(i, open);
    // 找配对 }
    let depth = 1;
    let j = open + 1;
    while (j < text.length && depth > 0) {
      if (text[j] === '{') depth++;
      else if (text[j] === '}') depth--;
      j++;
    }
    const body = text.slice(open + 1, j - 1);
    if (prelude.startsWith('@')) {
      const name = prelude.split(/\s/)[0];
      if (name === '@media' || name === '@supports') {
        tokens.push({ type: 'at-block', prelude, children: parseBlocks(body) });
      } else {
        // @keyframes / @font-face 等原样保留
        tokens.push({ type: 'raw', text: beforePrelude + '{' + body + '}' });
      }
    } else {
      // 普通规则：prelude 是选择器列表；body 是声明（可能含嵌套，手写 CSS 一般无）
      tokens.push({ type: 'rule', selector: prelude, body });
    }
    i = j;
  }
  return { tokens, tail: raw };
}

function serialize(tokens) {
  let out = '';
  for (const token of tokens) {
    if (token.type === 'raw') {
      out += token.text;
    } else if (token.type === 'at-block') {
      const inner = serialize(token.children.tokens);
      if (inner.trim()) {
        out += `${token.prelude} {${inner}}\n`;
      }
    } else if (token.type === 'rule') {
      const kept = token.selector.split(',').map((s) => s.trim()).filter((s) => s && !selectorIsUnused(s));
      if (kept.length > 0) {
        out += `${kept.join(',\n')} {${token.body}}\n`;
      }
    }
  }
  return out;
}

const { tokens } = parseBlocks(css);
const before = css.split('\n').length;
const result = serialize(tokens);
writeFileSync(cssPath + '.bak', css);
writeFileSync(cssPath, result);
console.log(`styles.css: ${before} 行 -> ${result.split('\n').length} 行`);
