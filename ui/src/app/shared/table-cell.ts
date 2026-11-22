/**
 * Display HTML for one cell of the backend's TableInterface, mirroring the Java getters
 * (TableInterface.TableCell*.getFormattedValue). The REST bridge serialises fields only, so
 * `formattedValue` is present only when the server set it explicitly; most cells carry their
 * text elsewhere:
 *  - TableCellText / ClassName / Time / Boolean: `value`
 *  - TableCellRooms: `rooms` = [name, color, id, preference][]
 *  - TableCellItems: `items` = [name, formatted, color, id][]
 *  - TableCellMulti / MultiLine: `chunks` = cells, concatenated
 *  - TableCellChange: `first` → `second`
 * Explicit formatted values keep their markup (rendered through Angular's sanitised
 * [innerHTML]); plain values are HTML-escaped.
 */
export interface AnyTableCell {
  formattedValue?: string | null;
  value?: unknown;
  rooms?: (string | null)[][];
  items?: (string | null)[][];
  chunks?: AnyTableCell[];
  first?: AnyTableCell | null;
  second?: AnyTableCell | null;
}

export function cellHtml(cell: AnyTableCell | null | undefined): string {
  if (!cell) return '';
  if (cell.formattedValue != null && cell.formattedValue !== '') return cell.formattedValue;
  if (Array.isArray(cell.rooms) && cell.rooms.length)
    return cell.rooms.map((r) => colored(escapeHtml(r?.[0] ?? ''), r?.[1])).join(', ');
  if (Array.isArray(cell.items) && cell.items.length)
    return cell.items.map((i) => colored(i?.[1] ?? escapeHtml(i?.[0] ?? ''), i?.[2])).join(', ');
  if (Array.isArray(cell.chunks) && cell.chunks.length) return cell.chunks.map((c) => cellHtml(c)).join('');
  if (cell.first !== undefined || cell.second !== undefined) {
    const a = cell.first ? cellHtml(cell.first) : 'N/A';
    const b = cell.second ? cellHtml(cell.second) : 'N/A';
    return a === b ? a : `${a} → ${b}`;
  }
  if (cell.value == null) return '';
  return escapeHtml(String(cell.value));
}

function colored(html: string, color: string | null | undefined): string {
  return color ? `<span style="color:${escapeHtml(color)}">${html}</span>` : html;
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
