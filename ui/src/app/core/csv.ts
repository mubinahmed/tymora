/**
 * Client-side CSV export. Serializes an already-loaded table (header + string
 * rows) and triggers a browser download — no backend/export servlet needed.
 * RFC-4180 quoting: wrap in double quotes and double any embedded quotes when
 * the cell contains a quote, comma, or newline.
 */
function csvCell(value: unknown): string {
  const s = value == null ? '' : String(value);
  return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}

/** Build CSV text from a header row and body rows. */
export function toCsv(columns: string[], rows: string[][]): string {
  const lines = [columns, ...rows].map((r) => r.map(csvCell).join(','));
  // Prepend a UTF-8 BOM so Excel opens non-ASCII correctly.
  return '﻿' + lines.join('\r\n');
}

/** Serialize a table to CSV and download it as <filename>.csv. */
export function downloadCsv(filename: string, columns: string[], rows: string[][]): void {
  const blob = new Blob([toCsv(columns, rows)], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.replace(/[^\w.-]+/g, '_').replace(/\.csv$/i, '') + '.csv';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
