import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { ApiError } from '../../core/models';
import { prefColor, prefLevel } from '../../shared/preference';
import { readableTextColor } from '../../shared/contrast';
import { ThemeService } from '../../core/theme.service';
import { ChangeRequestsService } from '../../core/change-requests.service';
import { MIN_REASON_LENGTH } from '../approval/change-requests.models';
import {
  ExamAssignmentDialogRequest,
  ExamAssignmentDialogResponse,
  ExamChange,
  ExamConflict,
  ExamPeriodOption,
  ExamPlacement,
  ExamRoomOption,
} from './exam-assignment-dialog.models';

const RPC = 'ExamAssignmentDialogRequest';

const CONFLICT_LABELS: Record<string, string> = {
  Direct: 'Direct conflict',
  MoreThanTwoADay: 'More than two exams a day',
  BackToBack: 'Back-to-back',
  DistanceBackToBack: 'Distance back-to-back',
  Distribution: 'Distribution constraint',
};

/**
 * Examination assignment dialog: the Angular counterpart of the legacy examInfo.action page.
 * For one exam it lists every available period with the student and instructor clashes it
 * would cause, the rooms free in the chosen period (capacity, preference), the pending change
 * with every exam it moves or bumps (before vs after conflict counts), solver suggestions,
 * and assigns the confirmed change.
 *
 * Backed by ExamAssignmentDialogRequest, which wraps the legacy ExamInfoModel: with an
 * examination solver loaded for the exam's type, changes go to the in-memory solution;
 * otherwise they are written to the database (and recorded in the change log).
 */
@Component({
  selector: 'app-exam-assignment-dialog',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    TextareaModule,
    FormsModule,
    DialogModule,
    ButtonModule,
    TagModule,
    TableModule,
    MessageModule,
    CheckboxModule,
    InputNumberModule,
    InputTextModule,
    SelectModule,
    ProgressSpinnerModule,
    TooltipModule,
  ],
  templateUrl: './exam-assignment-dialog.html',
  styleUrl: '../../shared/assignment-dialog.scss',
})
export class ExamAssignmentDialog {
  private rpc = inject(RpcService);
  private messages = inject(MessageService);
  private changes = inject(ChangeRequestsService);

  readonly visible = model<boolean>(false);
  readonly examId = input<number | null>(null);
  /** emitted after a change was assigned */
  readonly assigned = output<void>();

  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamAssignmentDialogResponse | null>(null);
  protected readonly confirming = signal(false);
  /** reason for the change (required, recorded with it) */
  protected reason = '';
  protected readonly minReason = MIN_REASON_LENGTH;
  protected readonly computing = signal(false);

  /** room picks not yet sent (sent once enough capacity is selected) */
  protected readonly roomPicks = signal<number[]>([]);

  // room filter + suggestion options (bound to inputs)
  protected minRoomSize: number | null = null;
  protected maxRoomSize: number | null = null;
  protected roomFilter = '';
  protected allowRoomConflict = false;
  protected roomOrder = 'SizeDesc';
  protected depth = 2;
  protected timeoutSec = 5;
  protected readonly roomOrders = [
    { label: 'Size (largest first)', value: 'SizeDesc' },
    { label: 'Size (smallest first)', value: 'SizeAsc' },
    { label: 'Name (A-Z)', value: 'NameAsc' },
    { label: 'Name (Z-A)', value: 'NameDesc' },
  ];

  private readonly theme = inject(ThemeService);
  /** Preference colours as TEXT, adjusted for readable contrast on the current theme (light and dark). */
  protected readonly prefColor = (prolog: string | null | undefined) => readableTextColor(prefColor(prolog), this.theme.isDark());

  protected readonly periods = computed(() => this.data()?.periods ?? []);
  protected readonly rooms = computed(() => this.data()?.rooms ?? null);
  protected readonly selectedPeriod = computed(() => this.periods().find((p) => p.selected) ?? null);
  protected readonly baseline = computed<ExamPlacement | null>(() => this.data()?.current ?? null);
  protected readonly pickCapacity = computed(() => {
    const ids = new Set(this.roomPicks());
    return (this.rooms() ?? []).filter((r) => r.roomId != null && ids.has(r.roomId)).reduce((s, r) => s + (r.capacity ?? 0), 0);
  });
  protected readonly hasChange = computed(() => (this.data()?.change?.lines?.length ?? 0) > 0);
  protected readonly bumped = computed(() => (this.data()?.change?.lines ?? []).filter((l) => l.unassigned && !l.selected));

  constructor() {
    effect(() => {
      const open = this.visible();
      const id = this.examId();
      if (open && id != null) untracked(() => this.load(id));
    });
  }

  // ------------------------------------------------------------------ calls

  private load(examId: number): void {
    this.data.set(null);
    this.error.set(null);
    this.confirming.set(false);
    this.loading.set(true);
    this.call({ operation: 'LOAD', examId }, () => this.loading.set(false));
  }

  private call(req: ExamAssignmentDialogRequest, done?: (r: ExamAssignmentDialogResponse) => void): void {
    this.busy.set(true);
    this.rpc.execute<ExamAssignmentDialogResponse>(RPC, req).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.computing.set(false);
        this.apply(r);
        done?.(r);
      },
      error: (e: ApiError) => {
        this.busy.set(false);
        this.computing.set(false);
        this.loading.set(false);
        if (!this.data()) this.error.set(e.message);
        else this.messages.add({ severity: 'error', summary: 'Examination assignment', detail: e.message });
      },
    });
  }

  private apply(r: ExamAssignmentDialogResponse): void {
    this.data.set(r);
    this.roomPicks.set((r.rooms ?? []).filter((x) => x.selected).map((x) => x.roomId!).filter((x) => x != null));
    this.minRoomSize = r.minRoomSize ?? null;
    this.maxRoomSize = r.maxRoomSize ?? null;
    this.roomFilter = r.roomFilter ?? '';
    this.allowRoomConflict = !!r.allowRoomConflict;
    this.roomOrder = r.roomOrder ?? 'SizeDesc';
    this.depth = r.depth || 2;
    this.timeoutSec = Math.round((r.timeout || 5000) / 1000);
    if (r.message) this.messages.add({ severity: 'warn', summary: 'Examination assignment', detail: r.message, life: 10000 });
  }

  protected selectPeriod(p: ExamPeriodOption): void {
    if (p.periodId == null || this.busy()) return;
    this.confirming.set(false);
    this.call({ operation: 'SELECT_PERIOD', periodId: p.periodId });
  }

  protected unassignPeriod(): void {
    this.confirming.set(false);
    this.call({ operation: 'SELECT_PERIOD', periodId: -1 });
  }

  /** Toggle a room; the selection is sent once the picked rooms seat all students. */
  protected toggleRoom(r: ExamRoomOption): void {
    if (r.roomId == null || this.busy()) return;
    const d = this.data();
    const max = Math.max(1, d?.maxRooms ?? 1);
    let ids = this.roomPicks().slice();
    if (ids.includes(r.roomId)) ids = ids.filter((x) => x !== r.roomId);
    else {
      ids.push(r.roomId);
      if (ids.length > max) ids = ids.slice(ids.length - max);
    }
    this.roomPicks.set(ids);
    if (this.pickCapacity() >= (d?.nrStudents ?? 0) && ids.length > 0) {
      this.confirming.set(false);
      this.call({ operation: 'SELECT_ROOMS', roomIds: ids });
    }
  }

  protected isRoomPicked(r: ExamRoomOption): boolean {
    return r.roomId != null && this.roomPicks().includes(r.roomId);
  }

  protected applyRoomFilter(): void {
    this.call({
      operation: 'REFRESH',
      minRoomSize: this.minRoomSize ?? -1,
      maxRoomSize: this.maxRoomSize ?? -1,
      roomFilter: this.roomFilter ?? '',
      allowRoomConflict: this.allowRoomConflict,
      roomOrder: this.roomOrder,
    });
  }

  protected selectExam(examId: number | undefined): void {
    if (examId == null || examId === this.data()?.examId) return;
    this.confirming.set(false);
    this.call({ operation: 'SELECT_EXAM', examId });
  }

  protected removeFromChange(examId: number | undefined, ev: Event): void {
    ev.stopPropagation();
    if (examId == null) return;
    this.call({ operation: 'REMOVE', examId });
  }

  protected clearChange(): void {
    this.confirming.set(false);
    this.call({ operation: 'CLEAR' });
  }

  protected computeSuggestions(deeper = false): void {
    if (deeper) this.depth = Math.min(this.depth + 1, 10);
    this.computing.set(true);
    this.call({ operation: 'SUGGESTIONS', depth: this.depth, timeout: Math.round(this.timeoutSec * 1000) });
  }

  protected pickSuggestion(index: number): void {
    this.confirming.set(false);
    this.call({ operation: 'SELECT_SUGGESTION', suggestionIndex: index });
  }

  protected reasonValid(): boolean {
    return this.reason.trim().length >= MIN_REASON_LENGTH;
  }

  protected assign(): void {
    if (!this.reasonValid()) return;
    const name = this.data()?.examName;
    this.call({ operation: 'ASSIGN', reason: this.reason.trim() }, (r) => {
      this.confirming.set(false);
      this.changes.refresh();
      if (r.submitted) {
        this.reason = '';
        this.messages.add({
          severity: 'info',
          summary: 'Sent for approval',
          detail: `${name}: nothing is changed until an approver approves the request (Change Requests page).`,
          life: 8000,
        });
      } else if (r.assigned) {
        this.reason = '';
        this.messages.add({
          severity: 'success',
          summary: 'Examination assigned',
          detail: r.solverMode
            ? `${name}: the in-memory exam solution was updated. Save or commit it from the solver page.`
            : `${name}: the assignment was saved and recorded in the change log.`,
        });
        this.assigned.emit();
      }
    });
  }

  // ------------------------------------------------------------------ display helpers

  protected conflictLabel(c: ExamConflict): string {
    return (c.instructor ? 'Instructor ' : 'Student ') + (CONFLICT_LABELS[c.type ?? ''] ?? c.type ?? '').toLowerCase();
  }

  protected prefName(p: string | undefined): string {
    return prefLevel(p).name;
  }

  /** "3 (+2)" style value with the difference to the current placement. */
  protected withDelta(value: number | undefined, base: number | undefined): string {
    const v = value ?? 0;
    const b = base ?? 0;
    if (!this.baseline() || v === b) return v ? String(v) : '';
    return `${v} (${v - b > 0 ? '+' : ''}${v - b})`;
  }

  protected deltaClass(value: number | undefined, base: number | undefined): string {
    if (!this.baseline()) return (value ?? 0) > 0 ? 'worse' : '';
    const d = (value ?? 0) - (base ?? 0);
    return d > 0 ? 'worse' : d < 0 ? 'better' : '';
  }

  /** colour for a before -> after pair (more conflicts = worse) */
  protected deltaClassBA(after: number | undefined, before: number | undefined): string {
    const d = (after ?? 0) - (before ?? 0);
    return d > 0 ? 'worse' : d < 0 ? 'better' : '';
  }

  protected beforeAfter(after: number | undefined, before: number | undefined): string {
    const a = after ?? 0;
    const b = before ?? 0;
    return a === b ? String(a) : `${b} → ${a}`;
  }

  protected changeSummary(c: ExamChange): string {
    return (c.lines ?? [])
      .map((l) => (l.unassigned ? `${l.exam}: unassigned` : `${l.exam}: ${l.toPeriod ?? ''} ${l.toRooms ?? ''}`.trim()))
      .join('; ');
  }

  protected periodTooltip(p: ExamPeriodOption): string {
    const parts = [this.prefName(p.pref)];
    if (p.distributionConflicts?.length) parts.push('Distribution: ' + p.distributionConflicts.join(', '));
    return parts.join(' · ');
  }

  protected close(): void {
    this.visible.set(false);
  }
}
