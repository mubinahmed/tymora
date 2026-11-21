import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('escapes HTML', () => {
    expect(renderMarkdown('<img src=x onerror=alert(1)>')).toBe('<p>&lt;img src=x onerror=alert(1)&gt;</p>');
  });
  it('keeps application links and drops others', () => {
    expect(renderMarkdown('[Open](/attendance)')).toContain('<a class="app-link" href="/attendance">Open</a>');
    expect(renderMarkdown('[x](https://evil.example)')).not.toContain('<a');
    expect(renderMarkdown('[x](javascript:alert(1))')).not.toContain('<a');
  });
  it('renders lists, tables and emphasis', () => {
    expect(renderMarkdown('- a\n- **b**')).toBe('<ul><li>a</li><li><strong>b</strong></li></ul>');
    const t = renderMarkdown('| A | B |\n|---|---|\n| 1 | 2 |');
    expect(t).toContain('<th>A</th>');
    expect(t).toContain('<td>2</td>');
    expect(t).not.toContain('---');
  });
});
