import { describe, expect, it } from 'vitest';
import {
  applyStepRichTextMarkup,
  moveItemByKey,
  parseStepRichTextBlocks
} from './stepRichText';

describe('stepRichText', () => {
  it('moves an item by drag source and drop target keys', () => {
    const items = [
      { id: 'prepare', label: '准备' },
      { id: 'submit', label: '提交' },
      { id: 'assert', label: '校验' }
    ];

    expect(moveItemByKey(items, (item) => item.id, 'assert', 'prepare').map((item) => item.id))
      .toEqual(['assert', 'prepare', 'submit']);
    expect(items.map((item) => item.id)).toEqual(['prepare', 'submit', 'assert']);
  });

  it('applies markdown-style marks around the selected step text', () => {
    const edit = applyStepRichTextMarkup('输入账号密码', 'bold', 2, 4);

    expect(edit.value).toBe('输入**账号**密码');
    expect(edit.selectionStart).toBe(2);
    expect(edit.selectionEnd).toBe(8);
  });

  it('parses rich text blocks for safe preview rendering', () => {
    const blocks = parseStepRichTextBlocks('点击 **提交**\n- 校验 `status`\n- _刷新_ 列表');

    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toMatchObject({
      kind: 'paragraph',
      segments: [
        { kind: 'text', text: '点击 ' },
        { kind: 'strong', text: '提交' }
      ]
    });
    expect(blocks[1]).toMatchObject({
      kind: 'list',
      items: [
        [
          { kind: 'text', text: '校验 ' },
          { kind: 'code', text: 'status' }
        ],
        [
          { kind: 'emphasis', text: '刷新' },
          { kind: 'text', text: ' 列表' }
        ]
      ]
    });
  });
});
