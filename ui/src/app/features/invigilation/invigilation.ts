import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
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
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';
import { PublicationPanel } from '../seating/publication-panel';
import {
  BoardCell,
  Candidate,
  Duty,
  InvigilationConfig,
  InvigilationRequest,
  InvigilationResponse,
  InvigilatorInfo,
  Issue,
  LogEntry,
} from '../seating/seating.models';

const RPC = 'InvigilationRequest';

/** The slot the candidate panel is for: an open slot or an existing duty (replacement). */
interface SlotPick {
  periodId: number;
  roomId?: number;
  room?: string;
  period?: string;
  dutyType: string;
  duty?: Duty;
}

/**
 * Invigilation (B1-6.10.x): invigilators with duty types, limits and availability (stored, self-service
 * or from approved staff absences), staffing rules, the duty board per period and venue with automatic
 * and manual allocation, replacement suggestions, conflicts, reports, approval, publication and the
 * audit trail. Backed by InvigilationRequest.
 */
@Component({
  selector: 'app-invigilation',
  imports: [
    FormsModule,
    ButtonModule,
    SelectModule,
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
    MultiSelectModule,
    PublicationPanel,
  ],
  templateUrl: './invigilation.html',
  styleUrl: './invigilation.scss',
})
export class Invigilation implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<InvigilationResponse | null>(null);
  protected readonly board = signal<BoardCell[]>([]);
  protected readonly issues = signal<Issue[]>([]);
  protected readonly invigilators = signal<InvigilatorInfo[]>([]);
  protected readonly duties = signal<Duty[]>([]);
  protected readonly log = signal<LogEntry[]>([]);
  protected readonly candidates = signal<Candidate[] | null>(null);
  protected readonly slot = signal<SlotPick | null>(null);
  protected readonly editInv = signal<InvigilatorInfo | null>(null);
  protected readonly importOpen = signal(false);
  protected tab = 'board';
  protected examTypeId: number | null = null;
  protected keepLocked = true;
  protected config: InvigilationConfig | null = null;
  protected csv = '';
  protected reportKind = 'byInvigilator';
  protected boardFilter: 'all' | 'problems' = 'all';
  protected readonly dutyTypeOptions = [
    { label: 'Chief invigilator', value: 'CHIEF' },
    { label: 'Invigilator', value: 'INVIGILATOR' },
    { label: 'Reserve', value: 'RESERVE' },
  ];
  protected readonly reportOptions = [
    { label: 'Duties by invigilator', value: 'byInvigilator' },
    { label: 'Duties by date and session', value: 'byDate' },
    { label: 'Duties by venue', value: 'byRoom' },
    { label: 'Workload', value: 'workload' },
    { label: 'Availability and unavailability', value: 'availability' },
    { label: 'Unfilled requirements', value: 'unfilled' },
    { label: 'Conflicts and exceptions', value: 'conflicts' },
  ];

  protected readonly typeOptions = computed(() => (this.data()?.examTypes ?? []).map((t) => ({ label: t.name, value: t.id })));
  protected readonly canEdit = computed(() => !!this.data()?.canEdit);
  protected readonly periodsWithCells = computed(() =>
    (this.data()?.periods ?? []).map((p) => ({ period: p, cells: this.board().filter((c) => c.periodId === p.id) })),
  );
  protected readonly deptOptions = computed(() => (this.data()?.departments ?? []).map((d) => ({ label: `${d.name} – ${d.detail}`, value: d.id })));

  ngOnInit(): void {
    this.page.set('Invigilation');
    this.call({ operation: 'INIT' }, true);
  }

  private call(req: InvigilationRequest, initial = false, after?: (r: InvigilationResponse) => void): void {
    if (initial) this.loading.set(true);
    this.busy.set(true);
    this.rpc.execute<InvigilationResponse>(RPC, { examTypeId: this.examTypeId, ...req }).subscribe({
      next: (r) => {
        this.data.set(r);
        this.examTypeId = r.examTypeId ?? null;
        if (r.board) this.board.set(r.board);
        if (r.issues) this.issues.set(r.issues);
        if (r.invigilators) this.invigilators.set(r.invigilators);
        if (r.duties) this.duties.set(r.duties);
        if (r.log) this.log.set(r.log);
        if (r.candidates) this.candidates.set(r.candidates);
        if (r.config && (!this.config || req.operation === 'SAVE_CONFIG' || req.operation === 'INIT' || req.operation === 'CONFIG')) this.config = { ...r.config };
        this.loading.set(false);
        this.busy.set(false);
        if (r.message) this.messages.add({ severity: 'success', summary: 'Invigilation', detail: r.message, life: 7000 });
        after?.(r);
      },
      error: (e: ApiError) => {
        this.loading.set(false);
        this.busy.set(false);
        if (!this.data()) this.error.set(e.message);
        else this.messages.add({ severity: 'error', summary: 'Invigilation', detail: e.message, life: 10000 });
      },
    });
  }

  protected onTypeChange(): void {
    this.config = null;
    this.call({ operation: 'INIT' });
  }

  protected onTab(t: string | number | undefined): void {
    this.tab = String(t);
    if (this.tab === 'board' || this.tab === 'problems') this.call({ operation: 'BOARD' });
    if (this.tab === 'invigilators') this.call({ operation: 'INVIGILATORS' });
    if (this.tab === 'reports') { this.call({ operation: 'DUTIES' }); this.call({ operation: 'BOARD' }); this.call({ operation: 'INVIGILATORS' }); }
    if (this.tab === 'publication') this.call({ operation: 'LOG' });
    if (this.tab === 'rules') this.call({ operation: 'CONFIG' });
  }

  protected allocate(): void {
    this.call({ operation: 'ALLOCATE', keepLocked: this.keepLocked });
  }

  // ------------------------------------------------------------------ board

  protected cellClass(c: BoardCell): string {
    if ((c.assigned ?? 0) < (c.required ?? 0)) return 'short';
    if ((c.duties ?? []).some((d) => (d.problems ?? []).length)) return 'warn';
    return 'ok';
  }

  protected visibleCells(cells: BoardCell[]): BoardCell[] {
    return this.boardFilter === 'all' ? cells : cells.filter((c) => this.cellClass(c) !== 'ok');
  }

  protected openSlots(c: BoardCell): string[] {
    const missing = Math.max(0, (c.required ?? 0) - (c.assigned ?? 0));
    const chiefCovered = (c.duties ?? []).some((d) => d.dutyType === 'CHIEF');
    const out: string[] = [];
    for (let i = 0; i < missing; i++) out.push(c.roomId == null ? 'RESERVE' : c.chiefRequired && !chiefCovered && i === 0 ? 'CHIEF' : 'INVIGILATOR');
    return out;
  }

  protected openCandidates(c: BoardCell, dutyType: string, duty?: Duty): void {
    const period = this.data()?.periods?.find((p) => p.id === c.periodId)?.name;
    this.slot.set({ periodId: c.periodId!, roomId: c.roomId, room: c.room, period, dutyType, duty });
    this.candidates.set(null);
    this.call({ operation: 'CANDIDATES', periodId: c.periodId, roomId: c.roomId, dutyType, dutyId: duty?.id });
  }

  protected assign(cand: Candidate, override = false): void {
    const s = this.slot();
    if (!s) return;
    this.call(
      { operation: 'ASSIGN', periodId: s.periodId, roomId: s.roomId, invigilatorId: cand.invigilatorId, dutyType: s.dutyType, dutyId: s.duty?.id, override },
      false,
      () => { this.slot.set(null); this.call({ operation: 'BOARD' }); },
    );
  }

  protected dutyAction(d: Duty, op: 'UNASSIGN' | 'LOCK' | 'UNLOCK'): void {
    this.call({ operation: op, dutyId: d.id }, false, () => this.call({ operation: 'BOARD' }));
    if (op === 'UNASSIGN') this.slot.set(null);
  }

  protected dutyLabel(t?: string): string {
    return t === 'CHIEF' ? 'Chief' : t === 'RESERVE' ? 'Reserve' : 'Invigilator';
  }

  // ------------------------------------------------------------------ invigilators

  protected edit(i?: InvigilatorInfo): void {
    this.editInv.set(i ? { ...i } : { active: true, dutyTypes: 'INVIGILATOR' });
  }

  protected dutyTypes(i: InvigilatorInfo): string[] {
    return (i.dutyTypes ?? '').split(',').filter((x) => x);
  }

  protected setDutyTypes(i: InvigilatorInfo, v: string[]): void {
    i.dutyTypes = v.join(',');
  }

  protected save(): void {
    const i = this.editInv();
    if (!i) return;
    this.call({ operation: 'SAVE_INVIGILATOR', invigilator: i }, false, () => this.editInv.set(null));
  }

  protected remove(i: InvigilatorInfo): void {
    this.call({ operation: 'DELETE_INVIGILATOR', invigilatorId: i.id });
  }

  protected doImport(): void {
    this.call({ operation: 'IMPORT', csv: this.csv }, false, () => { this.importOpen.set(false); this.csv = ''; });
  }

  protected availabilityOf(i: InvigilatorInfo, periodId?: number): string {
    return i.availability?.find((a) => a.id === periodId)?.name ?? '';
  }

  /** Cycle available -> unavailable -> preferred -> available (approved absences are fixed). */
  protected cycleAvailability(i: InvigilatorInfo, periodId?: number): void {
    if (!this.canEdit() || periodId == null) return;
    const cur = this.availabilityOf(i, periodId);
    if (cur === 'ABSENT') return;
    const next = cur === '' ? 'UNAVAILABLE' : cur === 'UNAVAILABLE' ? 'PREFERRED' : 'AVAILABLE';
    this.call({ operation: 'SET_AVAILABILITY', invigilatorId: i.id, periodId, availability: next });
  }

  // ------------------------------------------------------------------ rules

  protected saveConfig(): void {
    if (!this.config) return;
    this.call({ operation: 'SAVE_CONFIG', config: this.config }, false, () => this.call({ operation: 'BOARD' }));
  }

  // ------------------------------------------------------------------ reports

  protected reportRows(): { columns: string[]; rows: string[][] } {
    const periods = this.data()?.periods ?? [];
    switch (this.reportKind) {
      case 'byInvigilator':
        return { columns: ['Invigilator', 'Date', 'Time', 'Venue', 'Duty', 'Problems'], rows: this.duties().map((d) => [d.invigilator ?? '', d.date ?? '', d.time ?? '', d.room ?? '', this.dutyLabel(d.dutyType), (d.problems ?? []).join('; ')]) };
      case 'byDate':
        return {
          columns: ['Date', 'Time', 'Venue', 'Duty', 'Invigilator'],
          rows: [...this.duties()].sort((a, b) => (a.date + (a.time ?? '') + a.room).localeCompare(b.date + (b.time ?? '') + b.room)).map((d) => [d.date ?? '', d.time ?? '', d.room ?? '', this.dutyLabel(d.dutyType), d.invigilator ?? '']),
        };
      case 'byRoom':
        return {
          columns: ['Venue', 'Date', 'Time', 'Duty', 'Invigilator'],
          rows: [...this.duties()].sort((a, b) => ((a.room ?? '') + a.date + a.time).localeCompare((b.room ?? '') + b.date + b.time)).map((d) => [d.room ?? '', d.date ?? '', d.time ?? '', this.dutyLabel(d.dutyType), d.invigilator ?? '']),
        };
      case 'workload':
        return {
          columns: ['Invigilator', 'Department', 'Duty types', 'Duties', 'Max per day', 'Max total', 'Active'],
          rows: [...this.invigilators()].sort((a, b) => (b.nrDuties ?? 0) - (a.nrDuties ?? 0)).map((i) => [i.name ?? '', i.department ?? '', i.dutyTypes ?? '', String(i.nrDuties ?? 0), i.maxPerDay?.toString() ?? `default ${this.config?.defaultMaxPerDay}`, i.maxTotal?.toString() ?? `default ${this.config?.defaultMaxTotal}`, i.active ? 'yes' : 'no']),
        };
      case 'availability':
        return {
          columns: ['Invigilator', ...periods.map((p) => p.name ?? '')],
          rows: this.invigilators().map((i) => [i.name ?? '', ...periods.map((p) => this.availabilityOf(i, p.id).toLowerCase())]),
        };
      case 'unfilled':
        return {
          columns: ['Period', 'Venue', 'Students', 'Required', 'Assigned', 'Missing'],
          rows: this.board().filter((c) => (c.assigned ?? 0) < (c.required ?? 0)).map((c) => [periods.find((p) => p.id === c.periodId)?.name ?? '', c.room ?? '', String(c.students ?? 0), String(c.required ?? 0), String(c.assigned ?? 0), String((c.required ?? 0) - (c.assigned ?? 0))]),
        };
      default:
        return { columns: ['Severity', 'Period', 'Venue', 'Problem', 'What to do'], rows: this.issues().map((i) => [i.severity ?? '', i.period ?? '', i.room ?? '', i.message ?? '', i.action ?? '']) };
    }
  }

  protected exportReport(): void {
    const r = this.reportRows();
    downloadCsv(this.reportOptions.find((o) => o.value === this.reportKind)?.label ?? 'Invigilation', r.columns, r.rows);
  }

  // ------------------------------------------------------------------ publication

  protected decide(e: { op: string; note: string; override: boolean }): void {
    this.call({ operation: e.op, note: e.note, override: e.override }, false, () => this.call({ operation: 'LOG' }));
  }

  protected loadLog(): void {
    this.call({ operation: 'LOG' });
  }
}
