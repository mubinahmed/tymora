import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { ChangeRequestsService } from '../../core/change-requests.service';
import { AssignmentChangeInfo, MIN_REASON_LENGTH } from '../approval/change-requests.models';
import {
  ApiError,
  ClassAssignmentDetails,
  ClassAssignmentDetailsRequest,
  RoomInfo,
  SelectedAssignment,
  SelectedAssignmentsRequest,
  Suggestion,
  SuggestionProperties,
  Suggestions,
  SuggestionsInterface_ComputeSuggestionsRequest,
  TimeInfo,
} from '../../core/models';
import { intPrefColor, intPrefName, prefColor } from '../../shared/preference';
import { readableTextColor } from '../../shared/contrast';
import { ThemeService } from '../../core/theme.service';
import {
  KEY_CRITERIA,
  assignedSelection,
  roomsName,
  sameTime,
  selection,
  timeKey,
  timeName,
} from './class-assignment.util';

/** ComputeSuggestionsRequest exists in two interfaces, so the bridge needs the fully-qualified name. */
const COMPUTE_SUGGESTIONS = 'org.unitime.timetable.gwt.shared.SuggestionsInterface$ComputeSuggestionsRequest';

interface CriterionDelta {
  name: string;
  value: number;
  base: number;
}

/**
 * Class assignment dialog: the Angular counterpart of the legacy Suggestions page
 * (gwt.jsp?page=suggestions). For one class it shows the current placement and why it
 * conflicts, lets the planner pick another time and room(s), previews the impact before
 * anything is changed (moved and bumped classes, student, distribution and instructor
 * conflicts, change in every solver criterion), computes alternative placements, and
 * assigns the confirmed change.
 *
 * Backed by the existing SuggestionsInterface command beans (ClassAssignmentDetails,
 * SelectedAssignments, ComputeSuggestions, ComputeConflictTable, MakeAssignment). As in the
 * legacy UI, alternatives and changes need the course timetabling solver loaded in memory;
 * changes go to that in-memory solution and are saved or committed from Saved Timetables.
 */
@Component({
  selector: 'app-class-assignment-dialog',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    DialogModule,
    ButtonModule,
    TagModule,
    TableModule,
    MessageModule,
    CheckboxModule,
    InputNumberModule,
    ProgressSpinnerModule,
    TooltipModule,
    TextareaModule,
  ],
  templateUrl: './class-assignment-dialog.html',
  styleUrl: '../../shared/assignment-dialog.scss',
})
export class ClassAssignmentDialog {
  private rpc = inject(RpcService);
  private messages = inject(MessageService);
  private changes = inject(ChangeRequestsService);

  readonly visible = model<boolean>(false);
  readonly classId = input<number | null>(null);
  /** emitted after a change was assigned (or the class unassigned) */
  readonly assigned = output<void>();

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly solver = signal(false);
  protected readonly details = signal<ClassAssignmentDetails | null>(null);

  /** pending change: one SelectedAssignment per class the change touches */
  protected readonly selections = signal<SelectedAssignment[]>([]);
  protected readonly selectedTime = signal<TimeInfo | null>(null);
  protected readonly selectedRoomIds = signal<number[]>([]);

  protected readonly previewing = signal(false);
  protected readonly preview = signal<Suggestion | null>(null);
  protected readonly previewError = signal<string | null>(null);

  protected readonly computing = signal(false);
  protected readonly suggestions = signal<Suggestions | null>(null);
  protected depth = 2;
  protected timeLimit = 5;
  protected allowBreakHard = false;
  protected sameTimeOnly = false;
  protected sameRoomOnly = false;

  protected readonly placementsLoading = signal(false);
  protected readonly placements = signal<ClassAssignmentDetails[] | null>(null);

  /** confirmation step: assign the previewed change, or unassign the class */
  protected readonly confirming = signal<false | 'assign' | 'unassign'>(false);
  protected readonly assigning = signal(false);
  /** reason for the change (required, recorded with it) */
  protected reason = '';
  protected readonly minReason = MIN_REASON_LENGTH;
  protected readonly needsApproval = computed(() => !!this.changes.info()?.classNeedsApproval);
  /** pending change requests for this class */
  protected readonly pendingRequests = signal<AssignmentChangeInfo[]>([]);

  protected readonly timeName = timeName;
  protected readonly roomsName = roomsName;
  private readonly theme = inject(ThemeService);
  /** Preference colours as TEXT, adjusted for readable contrast on the current theme (light and dark). */
  protected readonly intPrefColor = (pref: number | null | undefined) => readableTextColor(intPrefColor(pref), this.theme.isDark());
  protected readonly intPrefName = intPrefName;
  protected readonly prefColor = (prolog: string | null | undefined) => readableTextColor(prefColor(prolog), this.theme.isDark());

  protected readonly className = computed(() => this.details()?.class?.name ?? 'Class');
  protected readonly nrRooms = computed(() => this.details()?.class?.nrRooms ?? 0);
  protected readonly isAssigned = computed(() => !!this.details()?.time);
  protected readonly instructors = computed(() =>
    (this.details()?.instructor ?? []).map((i) => i.name).filter((n) => !!n).join(', '),
  );
  protected readonly times = computed(() => this.details()?.times ?? []);
  protected readonly rooms = computed(() => this.details()?.rooms ?? []);
  protected readonly selectionComplete = computed(
    () => !!this.selectedTime() && this.selectedRoomIds().length === this.nrRooms(),
  );
  protected readonly canAssign = computed(() => {
    const p = this.preview();
    return this.solver() && !!p && p.canAssign !== false && !this.previewError() && this.pendingAssignments().length > 0;
  });
  protected readonly selectedRoomCapacity = computed(() => {
    const ids = new Set(this.selectedRoomIds());
    return this.rooms()
      .filter((r) => r.roomId != null && ids.has(r.roomId))
      .reduce((s, r) => s + (r.size ?? 0), 0);
  });

  /** Criteria whose value changes, key ones first, as (new, base) pairs. */
  protected readonly criteria = computed<CriterionDelta[]>(() => {
    const p = this.preview();
    if (!p) return [];
    const crit = p.criteria ?? {};
    const base = p.baseCriteria ?? {};
    const names = Array.from(new Set([...Object.keys(crit), ...Object.keys(base)]));
    names.sort((a, b) => {
      const ia = KEY_CRITERIA.indexOf(a);
      const ib = KEY_CRITERIA.indexOf(b);
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib) || a.localeCompare(b);
    });
    return names
      .map((name) => ({ name, value: crit[name] ?? 0, base: base[name] ?? 0 }))
      .filter((c) => Math.abs(c.value - c.base) > 1e-6);
  });

  /** what MakeAssignmentRequest receives: the moved classes plus the bumped ones (unassigned) */
  protected readonly pendingAssignments = computed<SelectedAssignment[]>(() => {
    const p = this.preview();
    if (!p) return [];
    const out: SelectedAssignment[] = [];
    for (const d of p.differentAssignments ?? []) {
      const s = assignedSelection(d);
      if (s) out.push(s);
    }
    for (const d of p.unresolvedConflicts ?? []) {
      if (d.class?.classId != null) out.push({ classId: d.class.classId });
    }
    return out;
  });

  constructor() {
    effect(() => {
      const open = this.visible();
      const id = this.classId();
      if (open && id != null) untracked(() => this.open(id));
    });
  }

  // ------------------------------------------------------------------ loading

  private open(classId: number): void {
    this.reset();
    this.loading.set(true);
    this.rpc.execute<SuggestionProperties>('SuggestionPropertiesRequest', {}).subscribe({
      next: (p) => {
        this.solver.set(!!p.solver);
        this.loadDetails(classId);
        this.loadPending(classId);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private reset(): void {
    this.error.set(null);
    this.details.set(null);
    this.selections.set([]);
    this.selectedTime.set(null);
    this.selectedRoomIds.set([]);
    this.preview.set(null);
    this.previewError.set(null);
    this.suggestions.set(null);
    this.placements.set(null);
    this.confirming.set(false);
  }

  private loadPending(classId: number): void {
    this.changes.call({ operation: 'LIST', filter: 'PENDING', subjectId: classId }).subscribe({
      next: (r) => this.pendingRequests.set(r.changes ?? []),
      error: () => this.pendingRequests.set([]),
    });
  }

  private loadDetails(classId: number, keepPending = false): void {
    this.loading.set(true);
    const req: ClassAssignmentDetailsRequest = { classId };
    this.rpc.execute<ClassAssignmentDetails>('ClassAssignmentDetailsRequest', req).subscribe({
      next: (d) => {
        this.details.set(d);
        this.loading.set(false);
        if (!keepPending) {
          // start from the current placement so a room-only or time-only change is one click
          this.selectedTime.set(d.time ?? null);
          this.selectedRoomIds.set((d.room ?? []).map((r) => r.roomId!).filter((x) => x != null));
        }
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.loading.set(false);
  }

  // ------------------------------------------------------------------ selection + preview

  protected isTimeSelected(t: TimeInfo): boolean {
    return sameTime(t, this.selectedTime());
  }

  protected isCurrentTime(t: TimeInfo): boolean {
    return sameTime(t, this.details()?.time);
  }

  protected isInitialTime(t: TimeInfo): boolean {
    return sameTime(t, this.details()?.initialTime);
  }

  protected isRoomSelected(r: RoomInfo): boolean {
    return r.roomId != null && this.selectedRoomIds().includes(r.roomId);
  }

  protected isCurrentRoom(r: RoomInfo): boolean {
    return (this.details()?.room ?? []).some((x) => x.roomId === r.roomId);
  }

  protected trackTime = (_: number, t: TimeInfo) => timeKey(t);

  protected selectTime(t: TimeInfo): void {
    if (!this.solver()) return;
    this.selectedTime.set(t);
    this.updatePreview();
  }

  protected toggleRoom(r: RoomInfo): void {
    if (!this.solver() || r.roomId == null) return;
    const id = r.roomId;
    const n = Math.max(1, this.nrRooms());
    let ids = this.selectedRoomIds().slice();
    if (ids.includes(id)) ids = ids.filter((x) => x !== id);
    else {
      ids.push(id);
      if (ids.length > n) ids = ids.slice(ids.length - n); // drop the oldest pick
    }
    this.selectedRoomIds.set(ids);
    this.updatePreview();
  }

  /** Preview the impact of the selected time + rooms (nothing is changed yet). */
  private updatePreview(): void {
    const d = this.details();
    const t = this.selectedTime();
    const classId = d?.class?.classId;
    if (!d || !t || classId == null || !this.selectionComplete()) {
      this.preview.set(null);
      this.previewError.set(null);
      return;
    }
    const mine = selection(classId, t, this.selectedRoomIds());
    const others = this.selections().filter((s) => s.classId !== classId);
    this.runPreview(classId, [...others, mine]);
  }

  private runPreview(classId: number, assignments: SelectedAssignment[]): void {
    this.selections.set(assignments);
    this.previewing.set(true);
    this.previewError.set(null);
    this.confirming.set(false);
    const req: SelectedAssignmentsRequest = { classId, assignments };
    this.rpc.execute<Suggestion>('SelectedAssignmentsRequest', req).subscribe({
      next: (s) => {
        this.preview.set(s);
        this.previewing.set(false);
      },
      error: (e: ApiError) => {
        // e.g. "instructor X not available at ... due to Y": the reason the placement is not allowed
        this.preview.set(null);
        this.previewError.set(e.message);
        this.previewing.set(false);
      },
    });
  }

  protected clearSelection(): void {
    const d = this.details();
    this.selections.set([]);
    this.preview.set(null);
    this.previewError.set(null);
    this.confirming.set(false);
    this.selectedTime.set(d?.time ?? null);
    this.selectedRoomIds.set((d?.room ?? []).map((r) => r.roomId!).filter((x) => x != null));
  }

  // ------------------------------------------------------------------ alternatives

  protected computeSuggestions(deeper = false): void {
    const classId = this.details()?.class?.classId;
    if (classId == null) return;
    if (deeper) this.depth = Math.min(this.depth + 1, 6);
    this.computing.set(true);
    const others = this.selections().filter((s) => s.classId !== classId);
    const req: SuggestionsInterface_ComputeSuggestionsRequest = {
      classId,
      assignments: others,
      depth: this.depth,
      limit: 20,
      timeLimit: Math.round(this.timeLimit * 1000),
      allowBreakHard: this.allowBreakHard,
      sameTime: this.sameTimeOnly,
      sameRoom: this.sameRoomOnly,
      placements: false,
    };
    this.rpc.execute<Suggestions>(COMPUTE_SUGGESTIONS, req).subscribe({
      next: (s) => {
        this.suggestions.set(s);
        this.computing.set(false);
      },
      error: (e: ApiError) => {
        this.computing.set(false);
        this.messages.add({ severity: 'error', summary: 'Alternatives failed', detail: e.message });
      },
    });
  }

  /** Adopt a suggestion: it becomes the pending change (preview = the suggestion itself). */
  protected pickSuggestion(s: Suggestion): void {
    const classId = this.details()?.class?.classId;
    const mine = (s.differentAssignments ?? []).find((d) => d.class?.classId === classId);
    if (mine?.assignedTime) {
      this.selectedTime.set(mine.assignedTime);
      this.selectedRoomIds.set((mine.assignedRoom ?? []).map((r) => r.roomId!).filter((x) => x != null));
    }
    this.selections.set(
      (s.differentAssignments ?? []).map((d) => assignedSelection(d)).filter((x): x is SelectedAssignment => !!x),
    );
    this.preview.set(s);
    this.previewError.set(null);
    this.confirming.set(false);
  }

  protected loadPlacements(): void {
    const classId = this.details()?.class?.classId;
    if (classId == null) return;
    this.placementsLoading.set(true);
    this.rpc.execute<ClassAssignmentDetails[]>('ComputeConflictTableRequest', { classId }).subscribe({
      next: (list) => {
        this.placements.set(list ?? []);
        this.placementsLoading.set(false);
      },
      error: (e: ApiError) => {
        this.placementsLoading.set(false);
        this.messages.add({ severity: 'error', summary: 'Placements failed', detail: e.message });
      },
    });
  }

  protected pickPlacement(p: ClassAssignmentDetails): void {
    if (!p.time) return;
    this.selectedTime.set(p.time);
    this.selectedRoomIds.set((p.room ?? []).map((r) => r.roomId!).filter((x) => x != null));
    this.updatePreview();
  }

  protected placementConflicts(p: ClassAssignmentDetails): string {
    const parts: string[] = [];
    const students = (p.studentConflicts ?? []).reduce((s, c) => s + (c.info?.jenrl ?? 0), 0);
    if (students) parts.push(`${students} student conflict${students === 1 ? '' : 's'}`);
    const dist = (p.distributionConflicts ?? []).filter((d) => d.info && !d.info.isSatisfied).length;
    if (dist) parts.push(`${dist} distribution`);
    const btb = (p.btbInstructorConflicts ?? []).length;
    if (btb) parts.push(`${btb} instructor back-to-back`);
    return parts.join(', ');
  }

  // ------------------------------------------------------------------ assign

  protected askAssign(): void {
    this.confirming.set('assign');
  }

  protected askUnassign(): void {
    this.confirming.set('unassign');
  }

  protected reasonValid(): boolean {
    return this.reason.trim().length >= MIN_REASON_LENGTH;
  }

  /** Submit the confirmed change with its reason: applied now, or sent for approval. */
  protected confirm(): void {
    const classId = this.details()?.class?.classId;
    if (classId == null || !this.reasonValid()) return;
    if (this.confirming() === 'unassign') {
      this.submit(classId, [{ classId }], `${this.className()}: ${timeName(this.details()?.time)} ${roomsName(this.details()?.room)} -> not assigned`);
    } else {
      this.submit(classId, this.pendingAssignments(), this.changeSummary());
    }
  }

  /** Text stored with the change and shown to the approver. */
  private changeSummary(): string {
    const p = this.preview();
    if (!p) return '';
    const lines = (p.differentAssignments ?? []).map((d) => {
      const f = this.fromTo(d);
      return `${d.class?.name}: ${f.fromTime} ${f.fromRoom} -> ${f.toTime} ${f.toRoom}`.replace(/\s+/g, ' ').trim();
    });
    for (const d of p.unresolvedConflicts ?? []) lines.push(`${d.class?.name}: ${timeName(d.time)} ${roomsName(d.room)} -> not assigned`);
    lines.push(`Not-assigned classes ${this.delta(p.unassignedVariables ?? 0, p.baseUnassignedVariables ?? 0)}; solution value ${this.delta(p.value ?? 0, p.baseValue ?? 0)}`);
    for (const c of this.criteria().slice(0, 6)) lines.push(`${c.name} ${this.delta(c.value, c.base)}`);
    return lines.join('\n');
  }

  private submit(classId: number, assignments: SelectedAssignment[], summary: string): void {
    if (!assignments.length) return;
    this.assigning.set(true);
    this.changes
      .call({ operation: 'SUBMIT_CLASS', classId, assignments, reason: this.reason.trim(), summary })
      .subscribe({
        next: (r) => {
          this.assigning.set(false);
          const pending = r.change?.status === 'PENDING';
          this.messages.add({
            severity: pending ? 'info' : 'success',
            summary: pending ? 'Sent for approval' : 'Change applied',
            detail: pending
              ? `${this.className()}: nothing is changed until an approver approves the request (Change Requests page).`
              : `${this.className()}: the in-memory solution was updated and the change recorded with your reason. Save or commit it from Saved Timetables.`,
            life: 8000,
          });
          if (!pending) this.assigned.emit();
          this.reason = '';
          this.reset();
          this.loadDetails(classId);
          this.loadPending(classId);
        },
        error: (e: ApiError) => {
          this.assigning.set(false);
          this.messages.add({ severity: 'error', summary: 'Change not submitted', detail: e.message });
        },
      });
  }

  // ------------------------------------------------------------------ display helpers

  protected fromTo(d: ClassAssignmentDetails): { fromTime: string; toTime: string; fromRoom: string; toRoom: string } {
    return {
      fromTime: d.time ? timeName(d.time) : 'not assigned',
      toTime: d.assignedTime ? timeName(d.assignedTime) : 'not assigned',
      fromRoom: d.time ? roomsName(d.room) : '',
      toRoom: d.assignedTime ? roomsName(d.assignedRoom) : '',
    };
  }

  protected delta(value: number, base: number): string {
    const d = value - base;
    const r = (x: number) => (Math.abs(x - Math.round(x)) < 1e-6 ? String(Math.round(x)) : x.toFixed(2));
    return d === 0 ? r(value) : `${r(value)} (${d > 0 ? '+' : ''}${r(d)})`;
  }

  protected deltaClass(value: number, base: number): string {
    const d = value - base;
    return d > 1e-6 ? 'worse' : d < -1e-6 ? 'better' : '';
  }

  protected suggestionSummary(s: Suggestion): string {
    return (s.differentAssignments ?? [])
      .map((d) => `${d.class?.name}: ${timeName(d.assignedTime)} ${roomsName(d.assignedRoom)}`)
      .join('; ');
  }

  protected studentConflictCount(s: Suggestion | null): number {
    return (s?.studentConflicts ?? []).reduce((sum, c) => sum + (c.info?.jenrl ?? 0), 0);
  }

  protected studentConflictFlags(c: { info?: { isHard?: boolean; isDistance?: boolean; isCommited?: boolean; isFixed?: boolean } }): string {
    const f: string[] = [];
    if (c.info?.isHard) f.push('hard');
    if (c.info?.isDistance) f.push('distance');
    if (c.info?.isCommited) f.push('committed');
    if (c.info?.isFixed) f.push('fixed');
    return f.join(', ');
  }

  protected classNames(list: ClassAssignmentDetails[] | null | undefined): string {
    return (list ?? []).map((o) => o.class?.name ?? '').join(', ');
  }

  protected close(): void {
    this.visible.set(false);
  }
}
