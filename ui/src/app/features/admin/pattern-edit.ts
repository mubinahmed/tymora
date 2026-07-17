import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Create / Edit Date Patterns (legacy datePatternEdit.action) and Time Patterns
 * (legacy timePatternEdit.action), unified behind the PatternEditRequest command
 * bean. A `kind` toggle (DATE|TIME) selects the entity for the current academic
 * session; LOAD lists patterns + type options + permission flags, SAVE
 * merges the descriptive fields of one existing pattern, DELETE removes one.
 *
 * Now also EDITABLE:
 *  - the DatePattern day bitmap, via a calendar grid of the session dates. LOAD
 *    returns the session calendar + each pattern's offered dates; SAVE rebuilds
 *    the exact pattern/offset string the legacy datePatternEdit page stores
 *    (faithful re-implementation of DatePattern.setPatternAndOffset).
 *  - the TimePattern day-combination / start-time grid. LOAD returns the current
 *    day-codes + start-slots + encoding constants; SAVE rewrites the
 *    TimePatternDays/TimePatternTime sets (mirrors TimePatternEditForm.update,
 *    gated on TimePattern.isEditable()).
 *
 * STILL DEFERRED (legacy JSP page only): the department / parent / child
 * (pattern-set) associations, number-of-weeks and creating a brand-new pattern.
 */

type Operation = 'LOAD' | 'SAVE' | 'DELETE';
type Kind = 'DATE' | 'TIME';

interface PatternTypeOption {
  id: number;
  label: string;
}

interface CalendarDate {
  key: number;
  year: number;
  month: number; // 0-based
  day: number;
  dayOfWeek: number; // 0=Mon..6=Sun
  holiday: number; // 0 none, 1 holiday, 2 break
}

interface PatternRecord {
  id?: number | null;
  name?: string;
  type?: number | null;
  typeLabel?: string;
  visible?: boolean;
  used?: boolean;
  default?: boolean;
  // time only
  nrMeetings?: number | null;
  minPerMtg?: number | null;
  slotsPerMtg?: number | null;
  breakTime?: number | null;
  // date only (read-only preview)
  patternPreview?: string;
  numberOfWeeks?: string;
  // date grid
  offeredDays?: number[] | null;
  patternEditable?: boolean;
  // time grid
  dayCodes?: number[] | null;
  startSlots?: number[] | null;
  gridEditable?: boolean;
}

interface PatternEditRequest {
  kind: Kind;
  operation: Operation;
  record?: PatternRecord;
}

interface PatternEditResponse {
  kind?: Kind;
  editable?: boolean;
  addable?: boolean;
  deletable?: boolean;
  records?: PatternRecord[];
  types?: PatternTypeOption[];
  // date
  sessionDates?: CalendarDate[];
  // time encoding constants
  dayCodes?: number[];
  dayNames?: string[];
  slotLengthMin?: number;
  firstSlotTimeMin?: number;
  slotsPerDay?: number;
}

interface CalendarMonth {
  label: string;
  cells: (CalendarDate | null)[];
}

interface Editing {
  id: number | null;
  name: string;
  type: number | null;
  visible: boolean;
  nrMeetings: number | null;
  minPerMtg: number | null;
  slotsPerMtg: number | null;
  breakTime: number | null;
  patternEditable: boolean;
  gridEditable: boolean;
}

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

@Component({
  selector: 'app-pattern-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    CheckboxModule,
    DialogModule,
    SelectModule,
    MultiSelectModule,
    SelectButtonModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './pattern-edit.html',
  styles: [
    `
      .cal-wrap { display: flex; flex-wrap: wrap; gap: 1rem; max-height: 50vh; overflow-y: auto; padding: 0.25rem; }
      .cal-month { border: 1px solid var(--surface-border, #dee2e6); border-radius: 6px; padding: 0.5rem; }
      .cal-month h4 { margin: 0 0 0.4rem; font-size: 0.85rem; text-align: center; }
      .cal-grid { display: grid; grid-template-columns: repeat(7, 1.9rem); gap: 2px; }
      .cal-grid .dow { font-size: 0.65rem; text-align: center; color: var(--text-color-secondary, #6c757d); }
      .cal-day { width: 1.9rem; height: 1.9rem; border: 1px solid var(--surface-border, #dee2e6); border-radius: 4px;
        font-size: 0.75rem; display: flex; align-items: center; justify-content: center; cursor: pointer; user-select: none;
        background: var(--surface-0, #fff); }
      .cal-day.blank { border: none; cursor: default; background: transparent; }
      .cal-day.holiday { background: #fdecea; }
      .cal-day.brk { background: #fff4e5; }
      .cal-day.offered { background: #cfe8ff; border-color: #2684ff; font-weight: 600; }
      .cal-day.ro { cursor: default; }
      .cal-legend { display: flex; gap: 1rem; flex-wrap: wrap; font-size: 0.75rem; margin: 0.25rem 0 0.5rem; align-items: center; }
      .cal-legend .sw { display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin-right: 4px; vertical-align: middle; border: 1px solid rgba(0,0,0,0.15); }
      .sw.offered { background: #cfe8ff; }
      .sw.holiday { background: #fdecea; }
      .sw.brk { background: #fff4e5; }

      .combo-row { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.4rem; flex-wrap: wrap; }
      .combo-days { display: flex; gap: 0.6rem; flex-wrap: wrap; }
      .combo-day { display: flex; flex-direction: column; align-items: center; font-size: 0.7rem; gap: 2px; }
      .grid-section { margin-top: 0.5rem; }
      .grid-section h4 { margin: 0.5rem 0 0.3rem; font-size: 0.9rem; }
      .grid-hint { color: var(--text-color-secondary, #6c757d); font-size: 0.78rem; margin: 0 0 0.4rem; }
    `,
  ],
})
export class PatternEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly kindOptions = [
    { label: 'Date Patterns', value: 'DATE' as Kind },
    { label: 'Time Patterns', value: 'TIME' as Kind },
  ];
  protected readonly kind = signal<Kind>('DATE');
  protected readonly isTime = computed(() => this.kind() === 'TIME');

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly records = signal<PatternRecord[]>([]);
  protected readonly types = signal<PatternTypeOption[]>([]);
  protected readonly editable = signal(false);
  protected readonly deletable = signal(false);

  protected readonly dialogVisible = signal(false);
  protected editing: Editing = this.blank();

  // Shared date calendar + time encoding constants (from LOAD).
  protected readonly sessionDates = signal<CalendarDate[]>([]);
  protected readonly dayNames = signal<string[]>([]);
  private dayCodesConst: number[] = [64, 32, 16, 8, 4, 2, 1];
  private slotLen = 5;
  private firstSlot = 0;
  private slotsPerDay = 288;

  protected readonly weekdayLabels = WEEKDAY_LABELS;

  // Date-grid editing state (set of offered CalendarDate keys).
  protected readonly offered = signal<Set<number>>(new Set());

  // Time-grid editing state.
  protected readonly dayCombos = signal<boolean[][]>([]); // each length 7 (Mon..Sun)
  protected readonly startSlots = signal<number[]>([]); // selected start slots

  protected readonly calendarMonths = computed<CalendarMonth[]>(() => {
    const dates = this.sessionDates();
    const map = new Map<string, CalendarMonth>();
    const order: string[] = [];
    for (const d of dates) {
      const k = d.year + '-' + d.month;
      let g = map.get(k);
      if (!g) {
        g = { label: MONTH_NAMES[d.month] + ' ' + d.year, cells: [] };
        map.set(k, g);
        order.push(k);
        for (let i = 0; i < d.dayOfWeek; i++) g.cells.push(null);
      }
      g.cells.push(d);
    }
    return order.map((k) => map.get(k)!);
  });

  /** All valid start times (slot -> label), used by the time-grid multiselect. */
  protected readonly timeOptions = computed<{ label: string; value: number }[]>(() => {
    const out: { label: string; value: number }[] = [];
    for (let slot = 0; slot < this.slotsPerDay; slot++) {
      out.push({ label: this.slotLabel(slot), value: slot });
    }
    return out;
  });

  constructor() {
    this.page.set('Date & Time Patterns');
    this.reload();
  }

  private blank(): Editing {
    return {
      id: null,
      name: '',
      type: null,
      visible: true,
      nrMeetings: null,
      minPerMtg: null,
      slotsPerMtg: null,
      breakTime: null,
      patternEditable: false,
      gridEditable: false,
    };
  }

  private slotLabel(slot: number): string {
    const min = this.firstSlot + slot * this.slotLen;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h < 10 ? '0' : ''}${h}:${m < 10 ? '0' : ''}${m}`;
  }

  switchKind(kind: Kind): void {
    if (kind === this.kind()) return;
    this.kind.set(kind);
    this.page.set(kind === 'TIME' ? 'Time Patterns' : 'Date Patterns');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send({ kind: this.kind(), operation: 'LOAD' }, () => this.loading.set(false), (e) => {
      this.error.set(e.message);
      this.loading.set(false);
    });
  }

  private send(request: PatternEditRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<PatternEditResponse>('PatternEditRequest', request).subscribe({
      next: (res) => {
        this.records.set(res.records ?? []);
        this.types.set(res.types ?? []);
        this.editable.set(!!res.editable);
        this.deletable.set(!!res.deletable);
        this.sessionDates.set(res.sessionDates ?? []);
        this.dayNames.set(res.dayNames ?? []);
        if (res.dayCodes && res.dayCodes.length === 7) this.dayCodesConst = res.dayCodes;
        if (res.slotLengthMin) this.slotLen = res.slotLengthMin;
        if (res.firstSlotTimeMin != null) this.firstSlot = res.firstSlotTimeMin;
        if (res.slotsPerDay) this.slotsPerDay = res.slotsPerDay;
        done();
      },
      error: (e: ApiError) => fail(e),
    });
  }

  typeLabel(id: number | null | undefined): string {
    if (id == null) return '';
    return this.types().find((t) => t.id === id)?.label ?? String(id);
  }

  edit(r: PatternRecord): void {
    if (!this.editable()) return;
    this.editing = {
      id: r.id ?? null,
      name: r.name ?? '',
      type: r.type ?? null,
      visible: r.visible !== false,
      nrMeetings: r.nrMeetings ?? null,
      minPerMtg: r.minPerMtg ?? null,
      slotsPerMtg: r.slotsPerMtg ?? null,
      breakTime: r.breakTime ?? null,
      patternEditable: !!r.patternEditable,
      gridEditable: !!r.gridEditable,
    };
    // seed date grid
    this.offered.set(new Set(r.offeredDays ?? []));
    // seed time grid
    this.dayCombos.set((r.dayCodes ?? []).map((dc) => this.dayCodeToBools(dc)));
    this.startSlots.set([...(r.startSlots ?? [])]);
    this.dialogVisible.set(true);
  }

  cancel(): void {
    this.dialogVisible.set(false);
  }

  // ----- date grid helpers -----

  isOffered(key: number): boolean {
    return this.offered().has(key);
  }

  toggleDate(d: CalendarDate | null): void {
    if (!d || !this.editing.patternEditable) return;
    this.offered.update((set) => {
      const next = new Set(set);
      if (next.has(d.key)) next.delete(d.key);
      else next.add(d.key);
      return next;
    });
  }

  dayCellClass(d: CalendarDate | null): string {
    if (!d) return 'cal-day blank';
    let c = 'cal-day';
    if (!this.editing.patternEditable) c += ' ro';
    if (this.isOffered(d.key)) c += ' offered';
    else if (d.holiday === 2) c += ' brk';
    else if (d.holiday === 1) c += ' holiday';
    return c;
  }

  // ----- time grid helpers -----

  private dayCodeToBools(dc: number): boolean[] {
    return this.dayCodesConst.map((code) => (dc & code) !== 0);
  }

  private boolsToDayCode(bools: boolean[]): number {
    let dc = 0;
    for (let i = 0; i < this.dayCodesConst.length; i++) if (bools[i]) dc += this.dayCodesConst[i];
    return dc;
  }

  addCombo(): void {
    this.dayCombos.update((list) => [...list, new Array(7).fill(false)]);
  }

  removeCombo(i: number): void {
    this.dayCombos.update((list) => list.filter((_, idx) => idx !== i));
  }

  submit(): void {
    const name = this.editing.name.trim();
    if (!name) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Name is required.' });
      return;
    }

    const record: PatternRecord = {
      id: this.editing.id,
      name,
      type: this.editing.type,
      visible: this.editing.visible,
    };
    if (this.isTime()) {
      record.nrMeetings = this.editing.nrMeetings;
      record.minPerMtg = this.editing.minPerMtg;
      record.slotsPerMtg = this.editing.slotsPerMtg;
      record.breakTime = this.editing.breakTime;
      if (this.editing.gridEditable) {
        const dayCodes: number[] = [];
        for (const c of this.dayCombos()) {
          const dc = this.boolsToDayCode(c);
          if (dc === 0) {
            this.messages.add({
              severity: 'error',
              summary: 'Validation',
              detail: 'A day combination has no days selected. Remove it or check at least one day.',
            });
            return;
          }
          dayCodes.push(dc);
        }
        record.dayCodes = dayCodes;
        record.startSlots = [...this.startSlots()].sort((a, b) => a - b);
      }
    } else if (this.editing.patternEditable) {
      record.offeredDays = Array.from(this.offered());
    }

    this.saving.set(true);
    this.send(
      { kind: this.kind(), operation: 'SAVE', record },
      () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: name });
      },
      (e) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    );
  }

  confirmDelete(r: PatternRecord): void {
    this.confirm.confirm({
      header: 'Delete pattern',
      message: `Delete "${r.name}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(r),
    });
  }

  private doDelete(r: PatternRecord): void {
    this.send(
      { kind: this.kind(), operation: 'DELETE', record: { id: r.id } },
      () => this.messages.add({ severity: 'success', summary: 'Deleted', detail: r.name }),
      (e) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    );
  }
}
