import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSessionInfo,
  AdvisingStudentDetails,
  AdvisorCourseRequestSubmission,
  AdvisorNote,
  ApiError,
  CheckCoursesResponse,
  CourseAssignment,
  CourseMessage,
  CourseRequestInterface,
  CourseRequestInterface_Request,
  DegreePlanInterface,
  FreeTime,
  RequestedCourse,
  StudentSectioningContext,
} from '../../core/models';
import { DegreePlanDialog, PickedCourse } from './degree-plan-dialog';

const SERVICE = 'sectioning.gwt';
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const FREE_PREFIX = /^free\b\s*/i;

/** A single course choice in a request row (a course, a free-time block, or raw text). */
interface Suggestion {
  label: string;
  courseId?: number;
  courseName?: string;
  courseTitle?: string;
  freeTime?: FreeTime[];
}

/** One course slot inside a request line. `value` is the picked suggestion or typed text. */
interface CourseItem {
  value: Suggestion | string | null;
}

/** An editable advisor course-recommendation line (primary or alternate). */
interface RequestLine {
  id: number;
  courses: CourseItem[];
  waitList: boolean;
  critical: boolean;
  advisorNote: string;
  advisorCredit: string;
}

/** A flattened read-only course-request line for the student-requests table. */
interface RecRow {
  priority: string;
  courses: string;
  credit: string;
  note: string;
  waitList: boolean;
  status: string;
}

/**
 * Advisor Course Recommendations (legacy AdvisorCourseRequestsPage, page key "acrf")
 * — classic RemoteService screen via /api/service/sectioning.gwt.
 *
 * Flow: (1) type a student external id -> getStudentSessions -> pick a session;
 * (2) getStudentAdvisingDetails -> AdvisingStudentDetails (identity, status, the
 * advisor's recommended requests, the student's own requests, credit note, wait-list
 * / critical modes, permissions); (3) edit the recommended requests with the same
 * course-finder / free-time / alternatives / reorder mechanics as the Scheduling
 * Assistant, plus per-request advisor note, advisor credit, critical flag and
 * wait-list; a session-wide advisor note; and the student status; (4) Validate via
 * checkAdvisingDetails; (5) Submit (or Export when read-only) via submitAdvisingDetails,
 * optionally emailing the student. Last advisor notes are shown when available.
 *
 * Defensive by design: never throws on empty / not-found / not-eligible states — shows
 * a message instead and keeps a guard when no student or no update permission.
 */
@Component({
  selector: 'app-advisor-recommendations',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
    AutoCompleteModule,
    CheckboxModule,
    DialogModule,
    TooltipModule,
    ConfirmDialogModule,
    DegreePlanDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './advisor-recommendations.html',
  styles: [
    `
      .acr-page { display: flex; flex-direction: column; gap: 0.75rem; }
      .search-bar { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
      .acr-select { min-width: 18rem; }
      .center { display: flex; justify-content: center; padding: 2rem; }
      .hint { color: var(--p-text-muted-color, #888); }
      .acr-inline-msg { display: block; margin: 0.25rem 0; }
      .inline-spin { width: 1.5rem; height: 1.5rem; }
      .student-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: 0.35rem 1.5rem; }
      .student-info .lbl { display: inline-block; min-width: 8rem; font-weight: 600; color: var(--p-text-muted-color, #666); }
      .status-row { display: flex; align-items: center; gap: 0.5rem; margin: 0.5rem 0; flex-wrap: wrap; }
      .req-head, .req-row {
        display: grid;
        grid-template-columns: 4rem 1fr 6rem 12rem;
        align-items: start;
        gap: 0.5rem;
        padding: 0.35rem 0;
      }
      .req-head { font-weight: 600; border-bottom: 1px solid var(--p-content-border-color, #eee); color: var(--p-text-muted-color, #666); }
      .req-row { border-bottom: 1px solid var(--p-content-border-color, #f3f3f3); }
      .col-pri { padding-top: 0.4rem; }
      .col-courses { display: flex; flex-direction: column; gap: 0.35rem; min-width: 0; }
      .course-item { display: flex; align-items: center; gap: 0.35rem; }
      .course-item .or { font-style: italic; color: var(--p-text-muted-color, #888); width: 1.6rem; }
      .acr-ac { flex: 1 1 auto; }
      .acr-ac ::ng-deep .p-autocomplete,
      .acr-ac ::ng-deep input { width: 100%; }
      .line-extra { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; padding-left: 2rem; }
      .line-extra input { width: 100%; }
      .line-extra .note { flex: 1 1 14rem; min-width: 10rem; }
      .line-extra .cr { width: 6rem; }
      .line-extra label { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.85rem; }
      .add-alt {
        align-self: flex-start; background: none; border: none; cursor: pointer;
        color: var(--p-primary-color, #2563eb); font-size: 0.8rem; padding: 0.1rem 0;
      }
      .col-act { display: flex; gap: 0.1rem; }
      .actions { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-top: 1rem; }
      .busy { display: inline-flex; align-items: center; gap: 0.4rem; color: var(--p-text-muted-color, #888); }
      .notes-area { width: 100%; }
      .submit-result { margin-top: 0.75rem; }
    `,
  ],
})
export class AdvisorRecommendations implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);

  protected studentId = '';

  protected readonly loading = signal(false);
  protected readonly loadingData = signal(false);
  protected readonly busy = signal(false);
  protected readonly busyLabel = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly sessions = signal<AcademicSessionInfo[]>([]);
  protected sessionId: number | null = null;

  protected readonly details = signal<AdvisingStudentDetails | null>(null);
  protected readonly checkResponse = signal<CheckCoursesResponse | null>(null);
  protected readonly submission = signal<AdvisorCourseRequestSubmission | null>(null);
  protected readonly lastNotes = signal<AdvisorNote[]>([]);

  protected readonly primary = signal<RequestLine[]>([]);
  protected readonly alternatives = signal<RequestLine[]>([]);
  protected readonly suggestions = signal<Suggestion[]>([]);

  protected advisorNote = '';
  protected statusRef: string | null = null;
  protected emailStudent = false;

  // Degree plans dialog
  protected readonly degreeDialog = signal(false);
  protected readonly degreePlans = signal<DegreePlanInterface[]>([]);

  private lineSeq = 0;

  protected readonly sessionOptions = computed(() =>
    this.sessions().map((s) => ({
      label: s.name ?? `${s.term ?? ''} ${s.year ?? ''} ${s.campus ?? ''}`.trim(),
      value: s.sessionId!,
    })),
  );

  // ---- Detail-derived gates ------------------------------------------------------
  protected readonly canUpdate = computed(() => !!this.details()?.canUpdate);
  protected readonly canEmail = computed(() => !!this.details()?.canEmail);
  protected readonly canDegreePlans = computed(() => !!this.details()?.degreePlan);
  protected readonly waitListMode = computed(() => this.details()?.mode ?? 'None');
  protected readonly showWaitList = computed(() => this.waitListMode() !== 'None');
  protected readonly criticalCheck = computed(() => this.details()?.criticalCheck ?? 0);
  protected readonly showCritical = computed(() => this.criticalCheck() > 0);
  protected readonly criticalLabelText = computed(() => this.criticalLabel(this.criticalCheck()));

  /** Details resolved but no student record was found for the id/session. */
  protected readonly noStudent = computed(() => {
    const d = this.details();
    return d != null && d.studentId == null;
  });

  protected readonly statusOptions = computed(() => {
    const d = this.details();
    if (!d) return [] as { label: string; value: string }[];
    const map = new Map<string, string>();
    if (d.currentStatus?.reference) map.set(d.currentStatus.reference, d.currentStatus.label ?? d.currentStatus.reference);
    for (const s of d.availableStatuses ?? []) {
      if (s.reference) map.set(s.reference, s.label ?? s.reference);
    }
    return Array.from(map, ([value, label]) => ({ label, value }));
  });

  /** Read-only view of the student's own course requests. */
  protected readonly studentRows = computed<RecRow[]>(() => this.toRows(this.details()?.studentRequest));

  /** Read-only view of the advisor's recommendations (used when the advisor cannot update). */
  protected readonly studentRowsFromAdvisor = computed<RecRow[]>(() => this.toRows(this.details()?.request));

  /** Confirmation messages (isError) from the last checkAdvisingDetails. */
  protected readonly checkErrors = computed<CourseMessage[]>(() =>
    (this.checkResponse()?.messages ?? []).filter((m) => m.error),
  );
  protected readonly checkWarnings = computed<CourseMessage[]>(() =>
    (this.checkResponse()?.messages ?? []).filter((m) => !m.error),
  );

  ngOnInit(): void {
    this.page.set('Advisor Course Recommendations');
  }

  // ---- Step 1: resolve student -> sessions --------------------------------------
  lookup(): void {
    const ext = this.studentId.trim();
    if (!ext) return;
    this.loading.set(true);
    this.error.set(null);
    this.searched.set(false);
    this.details.set(null);
    this.checkResponse.set(null);
    this.submission.set(null);
    this.lastNotes.set([]);
    this.sessions.set([]);
    this.sessionId = null;
    this.rpc.service<AcademicSessionInfo[]>(SERVICE, 'getStudentSessions', [ext]).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.primary) ?? sessions[0])?.sessionId ?? null;
        this.loading.set(false);
        if (this.sessionId != null) this.loadDetails();
        else this.error.set('No academic sessions found for this student.');
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onSessionChange(): void {
    this.loadDetails();
  }

  // ---- Step 2: load advising record ---------------------------------------------
  private loadDetails(): void {
    const ext = this.studentId.trim();
    if (!ext || this.sessionId == null) return;
    this.loadingData.set(true);
    this.error.set(null);
    this.searched.set(false);
    this.details.set(null);
    this.checkResponse.set(null);
    this.submission.set(null);
    this.lastNotes.set([]);
    this.rpc
      .service<AdvisingStudentDetails>(SERVICE, 'getStudentAdvisingDetails', [this.sessionId, ext])
      .subscribe({
        next: (d) => {
          this.details.set(d ?? null);
          this.statusRef = d?.currentStatus?.reference ?? null;
          this.advisorNote = d?.request?.creditNote ?? '';
          this.emailStudent = !!d?.emailOptionalToggleDefault;
          this.populateFromRequest(d?.request ?? null);
          this.searched.set(true);
          this.loadingData.set(false);
          if (d?.canUpdate) this.loadLastNotes();
        },
        error: (e: ApiError) => this.fail(e, true),
      });
  }

  private loadLastNotes(): void {
    this.rpc.service<AdvisorNote[]>(SERVICE, 'lastAdvisorNotes', [this.context()]).subscribe({
      next: (notes) => this.lastNotes.set(notes ?? []),
      error: () => this.lastNotes.set([]),
    });
  }

  private context(): StudentSectioningContext {
    return {
      online: true,
      sectioning: true,
      sessionId: this.sessionId ?? undefined,
      studentId: this.details()?.studentId ?? undefined,
    };
  }

  // ---- Editable request model ----------------------------------------------------
  private newLine(): RequestLine {
    return { id: ++this.lineSeq, courses: [{ value: null }], waitList: false, critical: false, advisorNote: '', advisorCredit: '' };
  }

  private populateFromRequest(req: CourseRequestInterface | null): void {
    const toLine = (r: CourseRequestInterface_Request): RequestLine => ({
      id: ++this.lineSeq,
      waitList: !!r.waitList,
      critical: (r.critical ?? 0) > 0,
      advisorNote: r.advisorNote ?? '',
      advisorCredit: r.advisorCredit ?? '',
      courses: (r.requestedCourse ?? []).map((rc) => ({ value: this.requestedToSuggestion(rc) })),
    });
    const primary = (req?.courses ?? []).map(toLine);
    const alts = (req?.alternatives ?? []).map(toLine);
    this.primary.set(primary.length ? primary : [this.newLine()]);
    this.alternatives.set(alts.length ? alts : [this.newLine()]);
    this.appendEmptyIfNeeded(this.primary);
    this.appendEmptyIfNeeded(this.alternatives);
  }

  private requestedToSuggestion(rc: RequestedCourse): Suggestion {
    if (rc.freeTime?.length) {
      return { label: 'Free ' + rc.freeTime.map((f) => this.freeTimeLabel(f)).join(', '), freeTime: rc.freeTime };
    }
    return {
      label: [rc.courseName, rc.courseTitle].filter(Boolean).join(' - '),
      courseId: rc.courseId,
      courseName: rc.courseName,
      courseTitle: rc.courseTitle,
    };
  }

  private appendEmptyIfNeeded(sig: typeof this.primary): void {
    const lines = sig();
    const last = lines[lines.length - 1];
    if (!last || this.lineHasValue(last)) {
      sig.set([...lines, this.newLine()]);
    }
  }

  private lineHasValue(line: RequestLine): boolean {
    return line.courses.some((c) => this.itemHasValue(c));
  }

  private itemHasValue(item: CourseItem): boolean {
    const v = item.value;
    if (!v) return false;
    if (typeof v === 'string') return v.trim().length > 0;
    return !!(v.courseId || v.freeTime?.length || v.courseName);
  }

  protected onItemChange(list: 'primary' | 'alternatives'): void {
    const sig = list === 'primary' ? this.primary : this.alternatives;
    this.appendEmptyIfNeeded(sig);
  }

  protected addAlternative(line: RequestLine): void {
    line.courses.push({ value: null });
    this.primary.set([...this.primary()]);
    this.alternatives.set([...this.alternatives()]);
  }

  protected removeCourseItem(line: RequestLine, idx: number): void {
    if (line.courses.length <= 1) {
      line.courses[0] = { value: null };
    } else {
      line.courses.splice(idx, 1);
    }
    this.primary.set([...this.primary()]);
    this.alternatives.set([...this.alternatives()]);
  }

  protected removeLine(list: 'primary' | 'alternatives', idx: number): void {
    const sig = list === 'primary' ? this.primary : this.alternatives;
    const lines = [...sig()];
    lines.splice(idx, 1);
    if (!lines.length) lines.push(this.newLine());
    sig.set(lines);
    this.appendEmptyIfNeeded(sig);
  }

  protected moveLine(list: 'primary' | 'alternatives', idx: number, dir: -1 | 1): void {
    const sig = list === 'primary' ? this.primary : this.alternatives;
    const lines = [...sig()];
    const j = idx + dir;
    if (j < 0 || j >= lines.length) return;
    [lines[idx], lines[j]] = [lines[j], lines[idx]];
    sig.set(lines);
  }

  protected priorityLabel(list: 'primary' | 'alternatives', idx: number): string {
    return list === 'primary' ? String(idx + 1) : `Alt ${idx + 1}`;
  }

  // ---- Course finder autocomplete -----------------------------------------------
  protected search(event: { query: string }): void {
    const query = (event.query ?? '').trim();
    const ft = this.parseFreeTime(query);
    if (ft) {
      this.suggestions.set([{ label: 'Free ' + ft.map((f) => this.freeTimeLabel(f)).join(', '), freeTime: ft }]);
      return;
    }
    if (query.length < 2) {
      this.suggestions.set([]);
      return;
    }
    this.rpc
      .service<CourseAssignment[]>(SERVICE, 'listCourseOfferings', [this.context(), null, query, 25])
      .subscribe({
        next: (list) => this.suggestions.set((list ?? []).map((c) => this.courseToSuggestion(c))),
        error: () => this.suggestions.set([]),
      });
  }

  private courseToSuggestion(c: CourseAssignment): Suggestion {
    const name = [c.subject, c.courseNbr].filter(Boolean).join(' ');
    return {
      label: [name, c.title].filter(Boolean).join(' - '),
      courseId: c.courseId,
      courseName: name,
      courseTitle: c.title,
    };
  }

  // ---- Build the DTO -------------------------------------------------------------
  private itemToRequestedCourse(item: CourseItem): RequestedCourse | null {
    const v = item.value;
    if (!v) return null;
    if (typeof v === 'string') {
      const s = v.trim();
      if (!s) return null;
      const ft = this.parseFreeTime(s);
      if (ft) return { freeTime: ft };
      return { courseName: s };
    }
    if (v.freeTime?.length) return { freeTime: v.freeTime };
    if (v.courseId || v.courseName) {
      return { courseId: v.courseId, courseName: v.courseName, courseTitle: v.courseTitle };
    }
    return null;
  }

  private lineToRequest(line: RequestLine): CourseRequestInterface_Request | null {
    const rcs = line.courses.map((c) => this.itemToRequestedCourse(c)).filter((x): x is RequestedCourse => !!x);
    if (!rcs.length) return null;
    const req: CourseRequestInterface_Request = { requestedCourse: rcs, waitList: line.waitList };
    if (this.showCritical() && line.critical) req.critical = this.criticalCheck();
    if (line.advisorNote.trim()) req.advisorNote = line.advisorNote.trim();
    if (line.advisorCredit.trim()) req.advisorCredit = line.advisorCredit.trim();
    return req;
  }

  private buildRequest(): CourseRequestInterface {
    return {
      ...this.context(),
      courses: this.primary()
        .map((l) => this.lineToRequest(l))
        .filter((x): x is CourseRequestInterface_Request => !!x),
      alternatives: this.alternatives()
        .map((l) => this.lineToRequest(l))
        .filter((x): x is CourseRequestInterface_Request => !!x),
      creditNote: this.advisorNote.trim() || undefined,
    };
  }

  /** Merge the editable list back onto the loaded details (keep fields we do not edit). */
  private buildDetails(): AdvisingStudentDetails {
    const d = this.details()!;
    const status =
      this.statusRef != null
        ? [d.currentStatus, ...(d.availableStatuses ?? [])].find((s) => s?.reference === this.statusRef) ?? d.currentStatus
        : d.currentStatus;
    return { ...d, request: this.buildRequest(), currentStatus: status };
  }

  private hasAnyCourse(): boolean {
    return this.primary().some((l) => this.lineHasValue(l)) || this.alternatives().some((l) => this.lineHasValue(l));
  }

  // ---- Actions -------------------------------------------------------------------
  validate(): void {
    if (!this.details()) return;
    this.runBusy(
      'Validating recommendations…',
      this.rpc.service<CheckCoursesResponse>(SERVICE, 'checkAdvisingDetails', [this.buildDetails()]),
      (resp) => {
        this.checkResponse.set(resp ?? null);
        const ok = !(resp?.messages ?? []).some((m) => m.error) && !resp?.errorMessage;
        this.messages.add({
          severity: ok ? 'success' : 'warn',
          summary: ok ? 'Recommendations are valid' : 'Recommendations have messages',
          detail: ok ? 'No problems found.' : 'See the messages below.',
        });
      },
    );
  }

  submit(): void {
    if (!this.details()) return;
    const update = this.canUpdate();
    if (update && !this.hasAnyCourse()) {
      this.messages.add({ severity: 'warn', summary: 'No courses', detail: 'Add at least one recommended course.' });
      return;
    }
    this.confirm.confirm({
      header: update ? 'Submit Recommendations' : 'Export Recommendations',
      message: update
        ? `Save the advisor course recommendations for ${this.details()?.studentName ?? 'this student'}?` +
          (this.canEmail() && this.emailStudent ? ' The student will be emailed.' : '')
        : 'Export the advisor course recommendations as a PDF?',
      icon: 'pi pi-check-circle',
      acceptLabel: update ? 'Submit' : 'Export',
      accept: () => this.doSubmit(update),
    });
  }

  private doSubmit(update: boolean): void {
    const email = update && this.canEmail() && this.emailStudent;
    this.runBusy(
      update ? 'Submitting recommendations…' : 'Exporting recommendations…',
      this.rpc.service<AdvisorCourseRequestSubmission>(SERVICE, 'submitAdvisingDetails', [this.buildDetails(), email]),
      (sub) => {
        this.submission.set(sub ?? null);
        if (sub?.pdf?.length) this.downloadPdf(sub);
        this.messages.add({
          severity: 'success',
          summary: update ? 'Recommendations submitted' : 'Recommendations exported',
          detail: sub?.updated
            ? 'The advisor course recommendations were saved.' + (email ? ' A confirmation email was sent.' : '')
            : 'The recommendations were exported.',
        });
        if (update) this.loadDetails();
      },
    );
  }

  private downloadPdf(sub: AdvisorCourseRequestSubmission): void {
    try {
      const bytes = Uint8Array.from(sub.pdf ?? []);
      const blob = new Blob([bytes], { type: 'application/pdf' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = (sub.name || 'advisor-course-requests') + '.pdf';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      /* ignore download failures — the message still reports success */
    }
  }

  applyNoteSuggestion(note: AdvisorNote): void {
    this.advisorNote = note.replaceString ?? note.displayString ?? this.advisorNote;
  }

  // ---- Degree plans --------------------------------------------------------------
  openDegreePlans(): void {
    this.runBusy(
      'Loading degree plans…',
      this.rpc.service<DegreePlanInterface[]>(SERVICE, 'listDegreePlans', [this.context()]),
      (plans) => {
        this.degreePlans.set(plans ?? []);
        this.degreeDialog.set(true);
      },
    );
  }

  onDegreeCoursesPicked(picked: PickedCourse[]): void {
    if (!picked.length) return;
    const lines = this.primary().filter((l) => this.lineHasValue(l));
    for (const p of picked) {
      const sug: Suggestion = {
        label: [p.courseName, p.courseTitle].filter(Boolean).join(' - '),
        courseId: p.courseId,
        courseName: p.courseName,
        courseTitle: p.courseTitle,
      };
      if (p.orWithPrevious && lines.length) {
        lines[lines.length - 1].courses.push({ value: sug });
      } else {
        lines.push({ id: ++this.lineSeq, courses: [{ value: sug }], waitList: false, critical: false, advisorNote: '', advisorCredit: '' });
      }
    }
    this.primary.set(lines);
    this.appendEmptyIfNeeded(this.primary);
    this.messages.add({ severity: 'success', summary: 'Courses added', detail: `${picked.length} course(s) added from the degree plan.` });
  }

  // ---- Read-only student requests ------------------------------------------------
  private toRows(req?: CourseRequestInterface | null): RecRow[] {
    if (!req) return [];
    const rows: RecRow[] = [];
    (req.courses ?? []).forEach((r, i) => rows.push(this.toRow(r, String(i + 1))));
    (req.alternatives ?? []).forEach((r, i) => rows.push(this.toRow(r, `Alt ${i + 1}`)));
    return rows;
  }

  private toRow(r: CourseRequestInterface_Request, priority: string): RecRow {
    const courses = (r.requestedCourse ?? []).map((c) => this.courseLabel(c)).filter(Boolean).join(' or ');
    const first = r.requestedCourse?.[0];
    return {
      priority,
      courses,
      credit: r.advisorCredit ?? this.creditRange(first),
      note: r.advisorNote ?? '',
      waitList: !!r.waitList,
      status: (first?.status ?? '').toString().replace(/_/g, ' '),
    };
  }

  private courseLabel(c?: RequestedCourse): string {
    if (!c) return '';
    if (c.freeTime?.length) return 'Free ' + c.freeTime.map((f) => this.freeTimeLabel(f)).join(', ');
    return [c.courseName, c.courseTitle].filter(Boolean).join(' - ');
  }

  private creditRange(c?: RequestedCourse): string {
    const cr = c?.credit;
    if (!cr?.length) return '';
    const min = cr[0];
    const max = cr[cr.length - 1];
    return min === max ? String(min) : `${min} - ${max}`;
  }

  private criticalLabel(critical?: number): string {
    switch (critical) {
      case 1:
        return 'Critical';
      case 2:
        return 'Important';
      case 3:
        return 'Vital';
      default:
        return 'Critical';
    }
  }

  private runBusy<T>(label: string, obs: import('rxjs').Observable<T>, onNext: (v: T) => void): void {
    this.busy.set(true);
    this.busyLabel.set(label);
    obs.subscribe({
      next: (v) => {
        this.busy.set(false);
        onNext(v);
      },
      error: (e: ApiError) => {
        this.busy.set(false);
        this.messages.add({ severity: 'error', summary: 'Request failed', detail: e.message });
      },
    });
  }

  // ---- Time / free-time helpers (mirror of the Scheduling Assistant) -------------
  private freeTimeLabel(f: FreeTime): string {
    const days = (f.days ?? []).map((d) => DAY_NAMES[d] ?? '?').join('');
    const start = this.slotToTime(f.start ?? 0);
    const end = this.minutesToTime(5 * ((f.start ?? 0) + (f.length ?? 0)));
    return `${days} ${start} - ${end}`;
  }

  private slotToTime(slot: number): string {
    return this.minutesToTime(5 * slot);
  }

  private minutesToTime(totalMin: number): string {
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    const ampm = h >= 12 && h < 24 ? 'p' : 'a';
    const hh = h > 12 ? h - 12 : h === 0 ? 12 : h;
    return `${hh}:${m < 10 ? '0' : ''}${m}${ampm}`;
  }

  private parseFreeTime(text: string): FreeTime[] | null {
    if (!text || !FREE_PREFIX.test(text)) return null;
    const body = text.replace(FREE_PREFIX, '').trim();
    if (!body) return null;
    const tokens = body.split(/[,;]/).map((t) => t.trim()).filter(Boolean);
    const out: FreeTime[] = [];
    for (const tok of tokens) {
      const ft = this.parseFreeToken(tok);
      if (!ft) return null;
      out.push(ft);
    }
    return out.length ? out : null;
  }

  private parseFreeToken(tok: string): FreeTime | null {
    const m = tok.match(/^([a-zA-Z]+)\s*(.*)$/);
    if (!m) return null;
    const days = this.parseDays(m[1]);
    if (!days.length) return null;
    const timePart = (m[2] ?? '').trim();
    if (!timePart) return null;
    const range = timePart.split(/-|to/i).map((s) => s.trim()).filter(Boolean);
    const startMin = this.parseClock(range[0]);
    if (startMin == null) return null;
    let endMin = range[1] != null ? this.parseClock(range[1], startMin) : null;
    if (endMin == null) endMin = startMin + 50;
    if (endMin <= startMin) return null;
    const start = Math.round(startMin / 5);
    const length = Math.round((endMin - startMin) / 5);
    if (start < 0 || start >= 24 * 12 || length <= 0) return null;
    return { days, start, length };
  }

  private parseDays(s: string): number[] {
    const out: number[] = [];
    const lower = s.toLowerCase();
    for (let i = 0; i < lower.length; i++) {
      const two = lower.substr(i, 2);
      if (two === 'th') { out.push(3); i++; continue; }
      if (two === 'su') { out.push(6); i++; continue; }
      switch (lower[i]) {
        case 'm': out.push(0); break;
        case 't': out.push(1); break;
        case 'w': out.push(2); break;
        case 'r': out.push(3); break;
        case 'f': out.push(4); break;
        case 's': out.push(5); break;
        case 'u': out.push(6); break;
        default: return [];
      }
    }
    return Array.from(new Set(out)).sort((a, b) => a - b);
  }

  private parseClock(s: string, ref?: number): number | null {
    if (!s) return null;
    const m = s.match(/^(\d{1,2})(?::(\d{2}))?\s*(am|pm|a|p)?$/i);
    if (!m) {
      const digits = s.match(/^(\d{3,4})$/);
      if (digits) {
        const n = digits[1].padStart(4, '0');
        const h = parseInt(n.slice(0, 2), 10);
        const min = parseInt(n.slice(2), 10);
        if (h > 23 || min > 59) return null;
        return h * 60 + min;
      }
      return null;
    }
    let h = parseInt(m[1], 10);
    const min = m[2] ? parseInt(m[2], 10) : 0;
    const ap = m[3]?.toLowerCase();
    if (ap === 'pm' || ap === 'p') { if (h < 12) h += 12; }
    else if (ap === 'am' || ap === 'a') { if (h === 12) h = 0; }
    else {
      if (h >= 1 && h <= 6) h += 12;
      if (ref != null && h * 60 + min < ref && h < 12) h += 12;
    }
    if (h > 23 || min > 59) return null;
    return h * 60 + min;
  }

  private fail(e: ApiError, data = false): void {
    this.error.set(e.message);
    (data ? this.loadingData : this.loading).set(false);
  }
}
