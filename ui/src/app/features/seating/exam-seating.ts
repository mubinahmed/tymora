import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabsModule } from 'primeng/tabs';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';
import { PublicationPanel } from './publication-panel';
import {
  Arrangement,
  ExamSeatingRequest,
  ExamSeatingResponse,
  IdName,
  Issue,
  LogEntry,
  RoomPlan,
  Seat,
  SeatLayout,
  SeatRow,
} from './seating.models';
import { DisplayDatePipe } from '../../shared/display-date.pipe';

const RPC = 'ExamSeatingRequest';
const PALETTE = ['#2563eb', '#16a34a', '#d97706', '#9333ea', '#db2777', '#0891b2', '#65a30d', '#dc2626', '#4f46e5', '#0d9488'];

/** A seat picked for moving: an occupied seat, or an unseated student. */
interface Pick {
  periodId: number;
  studentId: number;
  examId: number;
  studentName?: string;
  exam?: string;
  roomId?: number;
  seat?: string;
}

/**
 * Examination Seating (B1-6.6.x): seating plans per period and venue, automatic allocation with
 * configurable sort order and seating pattern, manual moves / swaps / locks, venue seat layouts,
 * special arrangements, problems with corrective actions, seat lists (print, CSV, export for the
 * student information system), approval, publication and the audit trail. Backed by ExamSeatingRequest.
 */
@Component({
  selector: 'app-exam-seating',
  imports: [DisplayDatePipe, 
    FormsModule,
    ButtonModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    TableModule,
    TagModule,
    TabsModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    MessageModule,
    ProgressSpinnerModule,
    TooltipModule,
    PublicationPanel,
  ],
  templateUrl: './exam-seating.html',
  styleUrl: './exam-seating.scss',
})
export class ExamSeating implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamSeatingResponse | null>(null);
  protected readonly plan = signal<RoomPlan | null>(null);
  protected readonly unseated = signal<SeatRow[]>([]);
  protected readonly rooms = signal<IdName[]>([]);
  protected readonly issues = signal<Issue[]>([]);
  protected readonly layouts = signal<SeatLayout[]>([]);
  protected readonly arrangements = signal<Arrangement[]>([]);
  protected readonly allRooms = signal<IdName[]>([]);
  protected readonly rows = signal<SeatRow[]>([]);
  protected readonly log = signal<LogEntry[]>([]);
  protected readonly pick = signal<Pick | null>(null);
  protected tab = 'plan';

  protected examTypeId: number | null = null;
  protected periodId: number | null = null;
  protected roomId: number | null = null;
  protected sort: string[] = ['EXAM', 'LAST_NAME', 'FIRST_NAME'];
  protected pattern = 'BLOCK';
  protected keepLocked = true;
  protected allPeriods = false;
  protected readonly sortOptions = [
    { label: 'School / department', value: 'SCHOOL' },
    { label: 'Examination (LU)', value: 'EXAM' },
    { label: 'Last name', value: 'LAST_NAME' },
    { label: 'First name', value: 'FIRST_NAME' },
    { label: 'Student id', value: 'STUDENT_ID' },
  ];
  protected readonly patternOptions = [
    { label: 'Block by paper', value: 'BLOCK' },
    { label: 'Alternate papers in adjacent seats', value: 'INTERLEAVE' },
  ];

  // list tab filters
  protected listPeriod: number | null = null;
  protected listRoom: string | null = null;
  protected listSearch = '';

  // dialogs
  protected readonly layoutEdit = signal<SeatLayout | null>(null);
  protected readonly arrangementEdit = signal<Arrangement | null>(null);

  protected readonly typeOptions = computed(() => (this.data()?.examTypes ?? []).map((t) => ({ label: t.name, value: t.id })));
  protected readonly periodOptions = computed(() =>
    (this.data()?.periods ?? []).map((p) => ({ label: `${p.name} · ${p.nrSeated}/${p.nrStudents} seated`, value: p.id })),
  );
  protected readonly roomOptions = computed(() => this.rooms().map((r) => ({ label: `${r.name} (${r.detail} seats)`, value: r.id })));
  protected readonly gridRows = computed(() => {
    const p = this.plan();
    if (!p) return [];
    const rows: Seat[][] = [];
    for (const s of p.seats ?? []) {
      (rows[s.row ?? 0] ??= []).push(s);
    }
    return rows;
  });
  protected readonly examColors = computed(() => {
    const m = new Map<number, string>();
    (this.plan()?.exams ?? []).forEach((e, i) => m.set(e.id!, PALETTE[i % PALETTE.length]));
    return m;
  });
  /** seat list filtered by the list tab's period / room / search (plain fields, so a method, not computed) */
  protected listRows(): SeatRow[] {
    const q = this.listSearch.trim().toLowerCase();
    return this.rows().filter(
      (r) =>
        (this.listPeriod == null || r.periodId === this.listPeriod) &&
        (this.listRoom == null || r.room === this.listRoom) &&
        (!q || [r.studentName, r.studentExternalId, r.exam, r.room, r.seat].join(' ').toLowerCase().includes(q)),
    );
  }
  protected listRoomOptions(): { label: string; value: string }[] {
    return Array.from(new Set(this.rows().filter((r) => this.listPeriod == null || r.periodId === this.listPeriod).map((r) => r.room ?? ''))).sort().map((r) => ({ label: r, value: r }));
  }
  protected readonly canEdit = computed(() => !!this.data()?.canEdit);

  ngOnInit(): void {
    this.page.set('Examination Seating');
    this.call({ operation: 'INIT' }, true);
  }

  // ------------------------------------------------------------------ calls

  private call(req: ExamSeatingRequest, initial = false, after?: (r: ExamSeatingResponse) => void): void {
    if (initial) this.loading.set(true);
    this.busy.set(true);
    const full: ExamSeatingRequest = { examTypeId: this.examTypeId, periodId: this.periodId, roomId: this.roomId, ...req };
    this.rpc.execute<ExamSeatingResponse>(RPC, full).subscribe({
      next: (r) => {
        this.apply(r);
        this.loading.set(false);
        this.busy.set(false);
        if (r.message) this.messages.add({ severity: 'success', summary: 'Seating', detail: r.message, life: 7000 });
        after?.(r);
      },
      error: (e: ApiError) => {
        this.loading.set(false);
        this.busy.set(false);
        if (!this.data()) this.error.set(e.message);
        else this.messages.add({ severity: 'error', summary: 'Seating', detail: e.message, life: 10000 });
      },
    });
  }

  private apply(r: ExamSeatingResponse): void {
    this.data.set(r);
    this.examTypeId = r.examTypeId ?? null;
    if (r.plan !== undefined) this.plan.set(r.plan ?? null);
    if (r.unseated) this.unseated.set(r.unseated);
    if (r.rooms && r.rooms.length) this.rooms.set(r.rooms);
    if (r.plan) this.roomId = r.plan.roomId ?? this.roomId;
    if (this.periodId == null && r.periods?.length) this.periodId = r.periods[0].id ?? null;
    if (r.issues) this.issues.set(r.issues);
    if (r.layouts) this.layouts.set(r.layouts);
    if (r.arrangements) this.arrangements.set(r.arrangements);
    if (r.allRooms) this.allRooms.set(r.allRooms);
    if (r.rows) this.rows.set(r.rows);
    if (r.log) this.log.set(r.log);
  }

  protected onTypeChange(): void {
    this.periodId = null;
    this.roomId = null;
    this.pick.set(null);
    this.call({ operation: 'INIT' });
  }

  protected onPeriodChange(): void {
    this.roomId = null;
    this.rooms.set([]);
    this.call({ operation: 'ROOM' });
  }

  protected onRoomChange(): void {
    this.call({ operation: 'ROOM' });
  }

  protected onTab(t: string | number | undefined): void {
    this.tab = String(t);
    if (this.tab === 'layouts') this.call({ operation: 'LAYOUTS' });
    if (this.tab === 'arrangements') this.call({ operation: 'ARRANGEMENTS' });
    if (this.tab === 'problems') this.call({ operation: 'VALIDATE' });
    if (this.tab === 'lists') this.call({ operation: 'LIST' });
    if (this.tab === 'publication') this.call({ operation: 'LOG' });
  }

  protected allocate(): void {
    this.pick.set(null);
    this.call({ operation: 'ALLOCATE', periodId: this.allPeriods ? null : this.periodId, sort: this.sort, pattern: this.pattern, keepLocked: this.keepLocked }, false, () =>
      this.call({ operation: 'ROOM' }),
    );
  }

  // ------------------------------------------------------------------ seat interaction

  protected seatColor(s: Seat): string {
    return s.examId != null ? (this.examColors().get(s.examId) ?? '#64748b') : '';
  }

  protected isPicked(s: Seat): boolean {
    const p = this.pick();
    return !!p && p.roomId === this.plan()?.roomId && p.seat === s.label;
  }

  protected clickSeat(s: Seat): void {
    if (!this.canEdit() || s.blocked) return;
    const p = this.pick();
    const roomId = this.plan()?.roomId;
    if (!p) {
      if (s.studentId != null && this.periodId != null)
        this.pick.set({ periodId: this.periodId, studentId: s.studentId, examId: s.examId!, studentName: s.studentName, exam: s.exam, roomId, seat: s.label });
      return;
    }
    if (p.roomId === roomId && p.seat === s.label) { this.pick.set(null); return; }
    if (!s.usable) {
      this.messages.add({ severity: 'warn', summary: 'Seating', detail: `${s.label} is not used in this period.` });
      return;
    }
    if (p.seat && s.studentId != null) {
      this.call({ operation: 'SWAP', periodId: p.periodId, roomId: p.roomId, seat: p.seat, toRoomId: roomId, toSeat: s.label }, false, () => this.call({ operation: 'ROOM' }));
    } else if (s.studentId == null) {
      this.call({ operation: 'MOVE', periodId: p.periodId, studentId: p.studentId, examId: p.examId, toRoomId: roomId, toSeat: s.label }, false, () => this.call({ operation: 'ROOM' }));
    } else {
      this.messages.add({ severity: 'warn', summary: 'Seating', detail: `${s.label} is taken; pick an empty seat for ${p.studentName}.` });
      return;
    }
    this.pick.set(null);
  }

  protected pickUnseated(r: SeatRow): void {
    if (!this.canEdit() || this.periodId == null) return;
    this.pick.set({ periodId: this.periodId, studentId: r.studentId!, examId: r.examId!, studentName: r.studentName, exam: r.exam });
  }

  protected seatAction(op: 'LOCK' | 'UNLOCK' | 'UNSEAT'): void {
    const p = this.pick();
    if (!p) return;
    this.call({ operation: op, periodId: p.periodId, studentId: p.studentId, examId: p.examId }, false, () => this.call({ operation: 'ROOM' }));
    this.pick.set(null);
  }

  protected pickedSeat(): Seat | undefined {
    const p = this.pick();
    return p?.seat ? (this.plan()?.seats ?? []).find((s) => s.label === p.seat && p.roomId === this.plan()?.roomId) : undefined;
  }

  // ------------------------------------------------------------------ problems

  protected goTo(i: Issue): void {
    if (i.periodId == null) return;
    this.periodId = i.periodId;
    this.roomId = i.roomId ?? null;
    this.tab = 'plan';
    this.call({ operation: 'ROOM' });
  }

  // ------------------------------------------------------------------ layouts

  protected editLayout(l?: SeatLayout): void {
    this.layoutEdit.set(l ? { ...l } : { roomId: undefined, rows: 10, columns: 10, frontRows: 1, blocked: '', accessible: '' });
  }

  protected layoutPreview(l: SeatLayout): { label: string; blocked: boolean; accessible: boolean; front: boolean }[][] {
    const blocked = new Set((l.blocked ?? '').toUpperCase().split(/[,;\s]+/).filter((x) => x));
    const accessible = new Set((l.accessible ?? '').toUpperCase().split(/[,;\s]+/).filter((x) => x));
    const out = [];
    for (let r = 0; r < Math.min(l.rows ?? 0, 60); r++) {
      const row = [];
      for (let c = 0; c < Math.min(l.columns ?? 0, 40); c++) {
        const label = this.seatLabel(r, c);
        row.push({ label, blocked: blocked.has(label), accessible: accessible.has(label), front: r < (l.frontRows ?? 0) });
      }
      out.push(row);
    }
    return out;
  }

  protected toggleLayoutSeat(l: SeatLayout, label: string, mode: 'blocked' | 'accessible'): void {
    const set = new Set((l[mode] ?? '').toUpperCase().split(/[,;\s]+/).filter((x) => x));
    if (set.has(label)) set.delete(label);
    else set.add(label);
    l[mode] = Array.from(set).join(',');
    this.layoutEdit.set({ ...l });
  }

  protected layoutClickMode: 'blocked' | 'accessible' = 'blocked';

  private seatLabel(row: number, col: number): string {
    let r = '';
    let n = row;
    do {
      r = String.fromCharCode(65 + (n % 26)) + r;
      n = Math.floor(n / 26) - 1;
    } while (n >= 0);
    return r + (col + 1);
  }

  protected saveLayout(): void {
    const l = this.layoutEdit();
    if (!l?.roomId) return;
    this.call({ operation: 'SAVE_LAYOUT', layout: l }, false, () => this.layoutEdit.set(null));
  }

  protected resetLayout(l: SeatLayout): void {
    this.call({ operation: 'DELETE_LAYOUT', layout: l });
  }

  // ------------------------------------------------------------------ arrangements

  protected editArrangement(a?: Arrangement): void {
    this.arrangementEdit.set(a ? { ...a } : { separateRoom: false, accessibleSeat: false, frontSeat: false, extraTimePct: null, roomId: null });
  }

  protected saveArrangement(): void {
    const a = this.arrangementEdit();
    if (!a) return;
    this.call({ operation: 'SAVE_ARRANGEMENT', arrangement: a }, false, () => this.arrangementEdit.set(null));
  }

  protected deleteArrangement(a: Arrangement): void {
    this.call({ operation: 'DELETE_ARRANGEMENT', id: a.id });
  }

  protected arrangementText(a: Arrangement): string {
    const p: string[] = [];
    if (a.separateRoom) p.push('separate room' + (a.room ? ` (${a.room})` : ''));
    if (a.accessibleSeat) p.push('accessible seat');
    if (a.frontSeat) p.push('front seat');
    if (a.extraTimePct) p.push(`${a.extraTimePct}% extra time`);
    return p.join(', ') || '—';
  }

  // ------------------------------------------------------------------ publication

  protected decide(e: { op: string; note: string; override: boolean }): void {
    this.call({ operation: e.op, note: e.note, override: e.override }, false, () => this.call({ operation: 'LOG' }));
  }

  protected loadLog(): void {
    this.call({ operation: 'LOG' });
  }

  // ------------------------------------------------------------------ output

  protected exportList(): void {
    downloadCsv(
      'Examination seats',
      ['Date', 'Time', 'Room', 'Seat', 'Student id', 'Student', 'Examination', 'School', 'Special arrangement', 'Extra time %'],
      this.listRows().map((r) => [r.date ?? '', r.time ?? '', r.room ?? '', r.seat ?? '', r.studentExternalId ?? '', r.studentName ?? '', r.exam ?? '', r.school ?? '', r.special ? 'yes' : '', r.extraTimePct?.toString() ?? '']),
    );
  }

  /** CSV for the student information system: one line per student and examination. */
  protected exportSims(): void {
    downloadCsv(
      'Examination seats - SIMS',
      ['STUDENT_ID', 'EXAM', 'EXAM_DATE', 'EXAM_TIME', 'VENUE', 'SEAT'],
      this.rows().map((r) => [r.studentExternalId ?? '', r.exam ?? '', r.date ?? '', r.time ?? '', r.room ?? '', r.seat ?? '']),
    );
  }

  /** Printable seating plan and attendance list of the current room. */
  protected printRoom(): void {
    const p = this.plan();
    if (!p) return;
    const period = this.data()?.periods?.find((x) => x.id === this.periodId)?.name ?? '';
    const esc = (s: string | undefined) => (s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
    let grid = '<table class="g">';
    for (const row of this.gridRows()) {
      grid += '<tr>' + row.map((s) => `<td class="${s.blocked ? 'b' : s.studentId != null ? 'o' : ''}"><b>${s.label}</b><br>${esc(s.studentExternalId)}</td>`).join('') + '</tr>';
    }
    grid += '</table>';
    const seated = (p.seats ?? []).filter((s) => s.studentId != null);
    const list = seated
      .map((s) => `<tr><td>${s.label}</td><td>${esc(s.studentExternalId)}</td><td>${esc(s.studentName)}</td><td>${esc(s.exam)}</td><td></td></tr>`)
      .join('');
    const w = window.open('', '_blank');
    if (!w) return;
    w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(p.room)} ${esc(period)}</title>
      <style>body{font-family:Arial,sans-serif;font-size:11px;margin:16px} h1{font-size:16px;margin:0} h2{font-size:13px}
      .g{border-collapse:collapse;margin:8px 0} .g td{border:1px solid #999;width:56px;height:30px;text-align:center;vertical-align:top;font-size:9px}
      .g td.b{background:#ddd} .g td.o{background:#eef4ff} .front{text-align:center;font-size:10px;color:#555;margin:4px 0}
      table.l{border-collapse:collapse;width:100%} table.l td,table.l th{border:1px solid #999;padding:3px 5px;text-align:left}
      @media print{.pb{page-break-before:always}}</style></head><body>
      <h1>Seating plan – ${esc(p.room)}</h1><div>${esc(period)} · ${seated.length} of ${p.usableSeats} seats used</div>
      <div class="front">FRONT</div>${grid}
      <h2 class="pb">Attendance list – ${esc(p.room)}, ${esc(period)}</h2>
      <table class="l"><tr><th>Seat</th><th>Student id</th><th>Name</th><th>Examination</th><th>Signature</th></tr>${list}</table>
      </body></html>`);
    w.document.close();
    w.focus();
    w.print();
  }
}
