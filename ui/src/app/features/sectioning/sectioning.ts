import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSessionInfo,
  ApiError,
  ClassAssignment,
  ClassAssignmentInterface,
  CheckCoursesResponse,
  CourseAssignment,
  CourseMessage,
  CourseRequestInterface,
  CourseRequestInterface_Request,
  DegreePlanInterface,
  EligibilityCheck,
  FreeTime,
  RequestedCourse,
  StudentSectioningContext,
} from '../../core/models';
import { SectioningGrid, GridItem } from './sectioning-grid';
import { DegreePlanDialog, PickedCourse } from './degree-plan-dialog';

const SERVICE = 'sectioning.gwt';
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/** EligibilityFlag ordinals from OnlineSectioningInterface.EligibilityFlag (bit = 1<<ordinal). */
const FLAG = {
  IS_GUEST: 2,
  CAN_USE_ASSISTANT: 3,
  CAN_ENROLL: 4,
  PIN_REQUIRED: 5,
  CAN_WAITLIST: 6,
  CAN_NO_SUBS: 7,
  GWT_CONFIRMATIONS: 14,
  DEGREE_PLANS: 15,
  CAN_REGISTER: 16,
  CAN_REQUIRE: 22,
} as const;

const FREE_PREFIX = /^free\b\s*/i;

/** A single course choice in a request row (a course, a free-time block, or raw text). */
export interface Suggestion {
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

/** An editable course-request line (primary or alternate). courses[0] is the request, [1..] are "or" choices. */
interface RequestLine {
  id: number;
  courses: CourseItem[];
  waitList: boolean;
}

/** Flattened class row for the schedule table. */
interface ScheduleRow {
  course: string;
  title: string;
  subpart: string;
  section: string;
  time: string;
  room: string;
  instructor: string;
  credit: string;
  note: string;
  saved: boolean;
  error: string;
}

/**
 * Interactive Student Scheduling Assistant — full port of the legacy GWT
 * StudentSectioningWidget (SECTIONING mode). Classic RemoteService screen via
 * /api/service/sectioning.gwt.
 *
 * Flow: pick a session -> checkEligibility (gates actions, may require a PIN) ->
 * load the student's saved requests + schedule -> edit the course-request list
 * (course finder autocomplete + free time + alternatives + wait-list) ->
 * Validate (checkCourses) / Build (section) / Save (saveRequest) / Enroll
 * (enroll) / Start Over. Degree Plans populate the request list.
 *
 * Defensive by design: never throws on empty/eligibility-blocked states; keeps
 * the null-student guard from the read-only version and shows messages instead.
 */
@Component({
  selector: 'app-scheduling-assistant',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
    AutoCompleteModule,
    CheckboxModule,
    DialogModule,
    InputTextModule,
    ConfirmDialogModule,
    TooltipModule,
    SectioningGrid,
    DegreePlanDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './sectioning.html',
  styles: [
    `
      .sa-page { display: flex; flex-direction: column; gap: 0.75rem; }
      .search-bar { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
      .sa-select { min-width: 20rem; }
      .center { display: flex; justify-content: center; padding: 2rem; }
      .hint { color: var(--p-text-muted-color, #888); }
      .sa-inline-msg { display: block; margin: 0.25rem 0; }
      .inline-spin { width: 1.5rem; height: 1.5rem; }
      .sa-card-head { display: flex; align-items: center; gap: 0.5rem; font-weight: 600; padding: 0.75rem 1rem 0; }
      .head-spacer { flex: 1 1 auto; }
      .req-sub { font-weight: 600; margin: 1rem 0 0.25rem; }
      .req-head, .req-row {
        display: grid;
        grid-template-columns: 5rem 1fr 5rem 8rem;
        align-items: start;
        gap: 0.5rem;
        padding: 0.35rem 0;
      }
      .req-head { font-weight: 600; border-bottom: 1px solid var(--p-content-border-color, #eee); color: var(--p-text-muted-color, #666); }
      .req-row { border-bottom: 1px solid var(--p-content-border-color, #f3f3f3); }
      .col-pri { padding-top: 0.4rem; }
      .col-courses { display: flex; flex-direction: column; gap: 0.25rem; min-width: 0; }
      .course-item { display: flex; align-items: center; gap: 0.35rem; }
      .course-item .or { font-style: italic; color: var(--p-text-muted-color, #888); width: 1.6rem; }
      .sa-ac { flex: 1 1 auto; }
      .sa-ac ::ng-deep .p-autocomplete,
      .sa-ac ::ng-deep input { width: 100%; }
      .add-alt {
        align-self: flex-start; background: none; border: none; cursor: pointer;
        color: var(--p-primary-color, #2563eb); font-size: 0.8rem; padding: 0.1rem 0;
      }
      .col-wl { padding-top: 0.4rem; }
      .col-act { display: flex; gap: 0.1rem; }
      .actions { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-top: 1rem; }
      .busy { display: inline-flex; align-items: center; gap: 0.4rem; color: var(--p-text-muted-color, #888); }
      .row-error td { color: #b91c1c; }
      .row-error-msg td { color: #b91c1c; font-size: 0.85em; padding-top: 0; }
      .pin-hint { color: var(--p-text-muted-color, #888); margin-bottom: 0.5rem; }
      .pin-input { width: 100%; }
    `,
  ],
})
export class Sectioning implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);

  protected readonly loading = signal(false);
  protected readonly loadingData = signal(false);
  protected readonly busy = signal(false);
  protected readonly busyLabel = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly sessions = signal<AcademicSessionInfo[]>([]);
  protected sessionId: number | null = null;
  private pin: string | null = null;

  protected readonly eligibility = signal<EligibilityCheck | null>(null);
  protected readonly schedule = signal<ClassAssignmentInterface | null>(null);
  protected readonly checkResponse = signal<CheckCoursesResponse | null>(null);

  protected readonly primary = signal<RequestLine[]>([]);
  protected readonly alternatives = signal<RequestLine[]>([]);

  /** Shared autocomplete result list — only the focused field's dropdown is visible. */
  protected readonly suggestions = signal<Suggestion[]>([]);

  protected readonly showGrid = signal(true);

  // Dialogs
  protected readonly pinDialog = signal(false);
  protected pinInput = '';
  protected readonly degreeDialog = signal(false);
  protected readonly degreePlans = signal<DegreePlanInterface[]>([]);

  private lineSeq = 0;

  protected readonly sessionOptions = computed(() =>
    this.sessions().map((s) => ({ label: s.name ?? `${s.term} ${s.year} ${s.campus}`.trim(), value: s.sessionId! })),
  );

  // ---- Eligibility-derived gates -------------------------------------------------
  protected readonly canUseAssistant = computed(() => this.flag(FLAG.CAN_USE_ASSISTANT));
  protected readonly canEnroll = computed(() => this.flag(FLAG.CAN_ENROLL));
  protected readonly canRegister = computed(() => this.flag(FLAG.CAN_REGISTER));
  protected readonly canWaitList = computed(() => this.flag(FLAG.CAN_WAITLIST));
  protected readonly canDegreePlans = computed(() => this.flag(FLAG.DEGREE_PLANS));
  protected readonly pinRequired = computed(() => this.flag(FLAG.PIN_REQUIRED));

  /** Eligibility resolved but no student is associated with this user/session. */
  protected readonly noStudent = computed(() => {
    const e = this.eligibility();
    return e != null && e.studentId == null;
  });

  /** Show the interactive editor only when eligible and a student is resolved. */
  protected readonly showEditor = computed(
    () => this.searched() && !this.noStudent() && (this.canUseAssistant() || this.canRegister()),
  );

  // ---- Schedule projections ------------------------------------------------------
  protected readonly scheduleRows = computed<ScheduleRow[]>(() => {
    const res = this.schedule();
    if (!res) return [];
    const rows: ScheduleRow[] = [];
    for (const course of res.assignments ?? []) {
      const classes = (course.assignments ?? []).filter((c) => !c.teachingAssigment);
      if (!classes.length) {
        rows.push(this.courseHeaderRow(course, null));
      } else {
        classes.forEach((c, i) => rows.push(this.courseHeaderRow(course, c, i === 0)));
      }
    }
    return rows;
  });

  protected readonly scheduleMessages = computed<string[]>(() => this.schedule()?.messages ?? []);
  protected readonly scheduleErrors = computed<string[]>(() =>
    (this.schedule()?.errors ?? []).map((e) => e.message ?? '').filter(Boolean),
  );

  /** Weekly-grid items derived from the built schedule + any free-time requests. */
  protected readonly gridItems = computed<GridItem[]>(() => {
    const items: GridItem[] = [];
    const res = this.schedule();
    let color = 0;
    for (const course of res?.assignments ?? []) {
      const name = [course.subject, course.courseNbr].filter(Boolean).join(' ');
      const ci = color++ % 8;
      for (const c of course.assignments ?? []) {
        if (c.teachingAssigment) continue;
        if (!c.days?.length || c.start == null || !c.length) continue;
        items.push({
          name,
          detail: [c.subpart, c.section].filter(Boolean).join(' ') + this.roomSuffix(c),
          days: c.days,
          start: c.start,
          length: c.length,
          breakTime: c.breakTime,
          colorIndex: ci,
        });
      }
    }
    // Free-time requests on the grid.
    for (const line of this.primary()) {
      for (const it of line.courses) {
        const v = it.value;
        if (v && typeof v === 'object' && v.freeTime?.length) {
          for (const ft of v.freeTime) {
            if (!ft.days?.length || ft.start == null || !ft.length) continue;
            items.push({ name: 'Free Time', days: ft.days, start: ft.start, length: ft.length, free: true });
          }
        }
      }
    }
    return items;
  });

  /** Confirmation messages (isError) shown inline after a Validate/Build. */
  protected readonly checkErrors = computed<CourseMessage[]>(() =>
    (this.checkResponse()?.messages ?? []).filter((m) => m.error && (m.confirm == null || m.confirm < 0)),
  );
  protected readonly checkWarnings = computed<CourseMessage[]>(() =>
    (this.checkResponse()?.messages ?? []).filter((m) => !m.error && (m.confirm == null || m.confirm < 0)),
  );

  ngOnInit(): void {
    this.page.set('Scheduling Assistant');
    this.loadSessions();
  }

  // ---- Loading -------------------------------------------------------------------
  private loadSessions(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.service<AcademicSessionInfo[]>(SERVICE, 'listAcademicSessions', [true]).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.primary) ?? sessions[0])?.sessionId ?? null;
        this.loading.set(false);
        if (this.sessionId != null) this.load();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private context(): StudentSectioningContext {
    return { online: true, sectioning: true, sessionId: this.sessionId ?? undefined, pin: this.pin ?? undefined };
  }

  onSessionChange(): void {
    this.pin = null;
    this.load();
  }

  load(): void {
    if (this.sessionId == null) return;
    const cx = this.context();
    this.loadingData.set(true);
    this.error.set(null);
    this.searched.set(false);
    this.eligibility.set(null);
    this.schedule.set(null);
    this.checkResponse.set(null);

    this.rpc.service<EligibilityCheck>(SERVICE, 'checkEligibility', [cx]).subscribe({
      next: (elig) => {
        this.eligibility.set(elig ?? null);
        if (this.pinRequired() && !this.pin) {
          // A PIN is needed before we can resolve the student — prompt for it.
          this.loadingData.set(false);
          this.searched.set(false);
          this.openPin();
          return;
        }
        if (elig?.studentId != null) {
          this.loadSaved(cx);
        } else {
          this.loadingData.set(false);
          this.searched.set(true);
        }
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  private loadSaved(cx: StudentSectioningContext): void {
    this.rpc.service<CourseRequestInterface>(SERVICE, 'savedRequest', [cx]).subscribe({
      next: (req) => {
        this.populateFromRequest(req);
        this.loadSchedule(cx);
      },
      error: () => {
        this.ensureRows();
        this.loadSchedule(cx);
      },
    });
  }

  private loadSchedule(cx: StudentSectioningContext): void {
    this.rpc.service<ClassAssignmentInterface>(SERVICE, 'savedResult', [cx]).subscribe({
      next: (res) => {
        this.schedule.set(res ?? null);
        this.searched.set(true);
        this.loadingData.set(false);
      },
      error: () => {
        this.searched.set(true);
        this.loadingData.set(false);
      },
    });
  }

  // ---- Editable request model ----------------------------------------------------
  private newLine(): RequestLine {
    return { id: ++this.lineSeq, courses: [{ value: null }], waitList: false };
  }

  private populateFromRequest(req: CourseRequestInterface | null): void {
    const toLine = (r: CourseRequestInterface_Request): RequestLine => ({
      id: ++this.lineSeq,
      waitList: !!r.waitList,
      courses: (r.requestedCourse ?? []).map((rc) => ({ value: this.requestedToSuggestion(rc) })),
    });
    const primary = (req?.courses ?? []).map(toLine);
    const alts = (req?.alternatives ?? []).map(toLine);
    this.primary.set(primary.length ? primary : [this.newLine()]);
    this.alternatives.set(alts.length ? alts : [this.newLine()]);
    // Always keep a trailing empty line to add more.
    this.appendEmptyIfNeeded(this.primary);
    this.appendEmptyIfNeeded(this.alternatives);
  }

  private ensureRows(): void {
    if (!this.primary().length) this.primary.set([this.newLine()]);
    if (!this.alternatives().length) this.alternatives.set([this.newLine()]);
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

  // ---- Course finder autocomplete -----------------------------------------------
  protected search(event: { query: string }): void {
    const query = (event.query ?? '').trim();
    // Free time entry: "Free MWF 7:30-8:20".
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
    return { requestedCourse: rcs, waitList: line.waitList };
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
    };
  }

  /** Flat list of currently assigned classes for the section/enroll currentAssignment arg. */
  private currentAssignment(): ClassAssignment[] {
    const res = this.schedule();
    if (!res) return [];
    const out: ClassAssignment[] = [];
    for (const course of res.assignments ?? []) {
      for (const c of course.assignments ?? []) {
        if (c.teachingAssigment || c.dummy) continue;
        out.push(c);
      }
    }
    return out;
  }

  private hasAnyCourse(): boolean {
    return this.primary().some((l) => this.lineHasValue(l)) || this.alternatives().some((l) => this.lineHasValue(l));
  }

  // ---- Actions -------------------------------------------------------------------
  /** checkCourses -> block on errors, confirm on confirmations, else run `proceed`. */
  private validateThen(proceed: (req: CourseRequestInterface) => void, label: string): void {
    if (!this.hasAnyCourse()) {
      this.messages.add({ severity: 'warn', summary: 'No courses', detail: 'Add at least one course request.' });
      return;
    }
    const req = this.buildRequest();
    this.busy.set(true);
    this.busyLabel.set('Checking courses…');
    this.rpc.service<CheckCoursesResponse>(SERVICE, 'checkCourses', [req]).subscribe({
      next: (resp) => {
        this.busy.set(false);
        this.checkResponse.set(resp ?? null);
        const msgs = resp?.messages ?? [];
        const isError = msgs.some((m) => m.error && (m.confirm == null || m.confirm < 0)) || !!resp?.errorMessage;
        if (isError) {
          this.messages.add({ severity: 'error', summary: 'Please fix the requests', detail: resp?.errorMessage ?? 'See messages below.' });
          return;
        }
        const confirms = msgs.filter((m) => m.confirm != null && m.confirm >= 0);
        if (confirms.length) {
          this.confirm.confirm({
            header: label,
            message: confirms.map((m) => `${m.course ? m.course + ': ' : ''}${m.message ?? ''}`).join('\n'),
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Continue',
            rejectLabel: 'Cancel',
            accept: () => proceed(req),
          });
          return;
        }
        proceed(req);
      },
      error: (e: ApiError) => {
        this.busy.set(false);
        this.messages.add({ severity: 'error', summary: 'Validation failed', detail: e.message });
      },
    });
  }

  validate(): void {
    if (!this.hasAnyCourse()) {
      this.messages.add({ severity: 'warn', summary: 'No courses', detail: 'Add at least one course request.' });
      return;
    }
    const req = this.buildRequest();
    this.runBusy('Checking courses…', this.rpc.service<CheckCoursesResponse>(SERVICE, 'checkCourses', [req]), (resp) => {
      this.checkResponse.set(resp ?? null);
      const ok = !(resp?.messages ?? []).some((m) => m.error) && !resp?.errorMessage;
      this.messages.add({
        severity: ok ? 'success' : 'warn',
        summary: ok ? 'Requests are valid' : 'Requests have messages',
        detail: ok ? 'No problems found.' : 'See the messages below.',
      });
    });
  }

  build(): void {
    this.validateThen((req) => {
      this.runBusy(
        'Building schedule…',
        this.rpc.service<ClassAssignmentInterface>(SERVICE, 'section', [req, this.currentAssignment()]),
        (res) => {
          this.schedule.set(res ?? null);
          this.messages.add({ severity: 'success', summary: 'Schedule built', detail: 'Review the proposed schedule below.' });
        },
      );
    }, 'Build Schedule');
  }

  save(): void {
    this.validateThen((req) => {
      this.runBusy(
        'Saving course requests…',
        this.rpc.service<CourseRequestInterface>(SERVICE, 'saveRequest', [req]),
        (saved) => {
          this.populateFromRequest(saved);
          this.messages.add({ severity: 'success', summary: 'Requests saved', detail: 'Your course requests were saved.' });
        },
      );
    }, 'Save Course Requests');
  }

  enroll(): void {
    if (!this.canEnroll()) return;
    this.validateThen((req) => {
      this.confirm.confirm({
        header: 'Enroll',
        message: 'Register into the built schedule now?',
        icon: 'pi pi-check-circle',
        acceptLabel: 'Enroll',
        accept: () => {
          this.runBusy(
            'Enrolling…',
            this.rpc.service<ClassAssignmentInterface>(SERVICE, 'enroll', [req, this.currentAssignment()]),
            (res) => {
              this.schedule.set(res ?? null);
              this.messages.add({ severity: 'success', summary: 'Enrolled', detail: 'Your registration was submitted.' });
            },
          );
        },
      });
    }, 'Enroll');
  }

  startOver(): void {
    this.confirm.confirm({
      header: 'Start Over',
      message: 'Clear all course requests and the current schedule? This does not change your saved registration.',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      acceptLabel: 'Start Over',
      accept: () => {
        this.primary.set([this.newLine()]);
        this.alternatives.set([this.newLine()]);
        this.schedule.set(null);
        this.checkResponse.set(null);
      },
    });
  }

  /** Wait-list validation: surface any override requirements from the server. */
  checkWaitList(): void {
    const req = this.buildRequest();
    this.runBusy(
      'Checking wait-list…',
      this.rpc.service<CheckCoursesResponse>(SERVICE, 'waitListCheckValidation', [req]),
      (resp) => {
        this.checkResponse.set(resp ?? null);
        const msgs = resp?.messages ?? [];
        this.messages.add({
          severity: msgs.length ? 'info' : 'success',
          summary: 'Wait-list check',
          detail: msgs.length ? 'See the messages below.' : 'No wait-list overrides needed.',
        });
      },
    );
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

  // ---- PIN -----------------------------------------------------------------------
  protected openPin(): void {
    this.pinInput = '';
    this.pinDialog.set(true);
  }

  protected submitPin(): void {
    const pin = this.pinInput.trim();
    if (!pin) return;
    this.pin = pin;
    this.pinDialog.set(false);
    this.load();
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
        lines.push({ id: ++this.lineSeq, courses: [{ value: sug }], waitList: false });
      }
    }
    this.primary.set(lines);
    this.appendEmptyIfNeeded(this.primary);
    this.messages.add({ severity: 'success', summary: 'Courses added', detail: `${picked.length} course(s) added from the degree plan.` });
  }

  // ---- Flag / display helpers ----------------------------------------------------
  private flag(ordinal: number): boolean {
    const flags = this.eligibility()?.flags ?? 0;
    return (flags & (1 << ordinal)) !== 0;
  }

  protected priorityLabel(list: 'primary' | 'alternatives', idx: number): string {
    return list === 'primary' ? String(idx + 1) : `Alt ${idx + 1}`;
  }

  private roomSuffix(c: ClassAssignment): string {
    const rooms = (c.rooms ?? []).map((r) => r.value).filter(Boolean).join(', ');
    return rooms ? ' · ' + rooms : '';
  }

  private courseHeaderRow(course: CourseAssignment, c: ClassAssignment | null, first = true): ScheduleRow {
    const courseName = [course.subject, course.courseNbr].filter(Boolean).join(' ');
    return {
      course: first ? courseName : '',
      title: first ? (course.title ?? '') : '',
      subpart: c?.subpart ?? '',
      section: c?.section ?? '',
      time: c ? this.classTime(c) : course.notAvailable ? 'Not Available' : '',
      room: (c?.rooms ?? []).map((r) => r.value).filter(Boolean).join(', '),
      instructor: (c?.instructos ?? []).filter(Boolean).join(', '),
      credit: c?.credit ?? (first ? (course.creditText ?? '') : ''),
      note: c?.note ?? '',
      saved: !!c?.saved,
      error: c?.error ?? course.conflictMessage ?? '',
    };
  }

  private classTime(c: ClassAssignment): string {
    if (!c.days?.length || c.start == null) return 'Arr Hrs';
    const days = c.days.map((d) => DAY_NAMES[d] ?? '?').join('');
    const start = this.slotToTime(c.start);
    const endSlots = c.start + (c.length ?? 0);
    const endMin = 5 * endSlots - (c.breakTime ?? 0);
    const end = this.minutesToTime(endMin);
    return `${days} ${start} - ${end}`;
  }

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

  // ---- Free-time parsing (mirror of FreeTimeParser) ------------------------------
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

  /** Parse a clock token to minutes-from-midnight. `ref` disambiguates am/pm for the end time. */
  private parseClock(s: string, ref?: number): number | null {
    if (!s) return null;
    const m = s.match(/^(\d{1,2})(?::(\d{2}))?\s*(am|pm|a|p)?$/i);
    if (!m) {
      // HHMM form
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
      // No meridiem: use school-hours heuristic (7..11 => am, else pm), and keep end after start.
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
