/**
 * A small, safe Markdown subset for assistant replies: everything is HTML-escaped first, then paragraphs,
 * headings, bullet / numbered lists, tables, **bold**, *italic*, `code` and links. Links are kept only for
 * application routes ("/attendance"); any other URL is shown as text. The result is passed through
 * Angular's [innerHTML] sanitizer as well.
 */
function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function inline(s: string): string {
  let t = esc(s);
  t = t.replace(/`([^`]+)`/g, '<code>$1</code>');
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  t = t.replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
  t = t.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_m, label: string, url: string) =>
    /^\/[A-Za-z0-9/_?=&.%-]*$/.test(url) ? `<a class="app-link" href="${url}">${label}</a>` : `${label} (${url})`);
  return t;
}

export function renderMarkdown(src: string): string {
  const lines = (src ?? '').replace(/\r/g, '').split('\n');
  const out: string[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i++; continue; }
    if (/^\s*\|.*\|\s*$/.test(line)) {
      const rows: string[][] = [];
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
        const cells = lines[i].trim().slice(1, -1).split('|').map((c) => c.trim());
        if (!cells.every((c) => /^:?-{2,}:?$/.test(c))) rows.push(cells);
        i++;
      }
      const [head, ...body] = rows;
      out.push('<table><thead><tr>' + head.map((c) => `<th>${inline(c)}</th>`).join('') + '</tr></thead><tbody>' +
        body.map((r) => '<tr>' + r.map((c) => `<td>${inline(c)}</td>`).join('') + '</tr>').join('') + '</tbody></table>');
      continue;
    }
    const h = /^(#{1,4})\s+(.*)$/.exec(line);
    if (h) { out.push(`<h4>${inline(h[2])}</h4>`); i++; continue; }
    if (/^\s*([-*•])\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*([-*•])\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*([-*•])\s+/, ''));
      out.push('<ul>' + items.map((x) => `<li>${inline(x)}</li>`).join('') + '</ul>');
      continue;
    }
    if (/^\s*\d+[.)]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*\d+[.)]\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*\d+[.)]\s+/, ''));
      out.push('<ol>' + items.map((x) => `<li>${inline(x)}</li>`).join('') + '</ol>');
      continue;
    }
    const para: string[] = [];
    while (i < lines.length && lines[i].trim() && !/^\s*(\||#{1,4}\s|[-*•]\s|\d+[.)]\s)/.test(lines[i])) para.push(lines[i++]);
    out.push(`<p>${para.map(inline).join('<br>')}</p>`);
  }
  return out.join('');
}
