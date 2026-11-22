import { Pipe, PipeTransform } from '@angular/core';

/**
 * Shows a date as dd/MM/yyyy (the app-wide date format), whatever form it arrives in:
 *  - ISO date "2026-10-19"                 -> "19/10/2026"
 *  - ISO date-time "2026-10-19T14:05:00…"  -> "19/10/2026 14:05"
 *  - Date object or epoch milliseconds     -> "19/10/2026 14:05" (time omitted when it is midnight)
 * Anything else (text the server already formatted, labels, empty values) is returned unchanged,
 * so the pipe is safe on fields whose format is not known in advance.
 *
 *   {{ row.date | displayDate }}
 */
@Pipe({ name: 'displayDate' })
export class DisplayDatePipe implements PipeTransform {
  transform(value: unknown): string {
    return displayDate(value);
  }
}

const pad = (n: number) => String(n).padStart(2, '0');

export function displayDate(value: unknown): string {
  if (value == null || value === '') return '';
  if (value instanceof Date || typeof value === 'number') {
    const d = value instanceof Date ? value : new Date(value);
    if (isNaN(d.getTime())) return String(value);
    const date = `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
    return d.getHours() || d.getMinutes() ? `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}` : date;
  }
  const s = String(value);
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/.exec(s);
  if (!m) return s;
  const date = `${m[3]}/${m[2]}/${m[1]}`;
  return m[4] != null ? `${date} ${m[4]}:${m[5]}` : date;
}
