import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSessionInfo,
  ApiError,
  ClassAssignment,
  ClassAssignmentInterface,
  CourseAssignment,
  CourseRequestInterface,
  CourseRequestInterface_Request,
  EligibilityCheck,
  FreeTime,
  RequestedCourse,
  StudentSectioningContext,
} from '../../core/models';

const SERVICE = 'sectioning.gwt';
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/** A flattened course-request line for the requests table. */
interface RequestRow {
  priority: string;
  courses: string;
  waitList: boolean;
  status: string;
}

/** A flattened class row for the schedule table. */
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
}

/**
 * Scheduling Assistant (legacy StudentSectioningWidget / StudentSectioningPage) —
 * classic RemoteService screen via /api/service/sectioning.gwt.
 *
 * Functional core: pick an academic session (listAcademicSessions), run the
 * eligibility check (checkEligibility) to surface access/status messages, then
 * load and display the student's saved course requests (savedRequest) and their
 * current class schedule (savedResult). The student is resolved server-side from
 * the logged-in user / masquerade context (online sectioning, sectioning mode).
 *
 * Deferred vs. the GWT original: interactive course-request entry + course finder,
 * running the sectioning solver / building a schedule, enroll / save / start-over
 * actions, degree plans, waitlists, special registrations, grade-mode changes,
 * PIN authentication, the weekly time-grid view, and advisor/admin student lookup.
 * This is a read-only view of the current requests and schedule.
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
  ],
  templateUrl: './sectioning.html',
})
export class Sectioning implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly loadingData = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly sessions = signal<AcademicSessionInfo[]>([]);
  protected sessionId: number | null = null;

  protected readonly eligibility = signal<EligibilityCheck | null>(null);
  protected readonly request = signal<CourseRequestInterface | null>(null);
  protected readonly schedule = signal<ClassAssignmentInterface | null>(null);

  protected readonly sessionOptions = computed(() =>
    this.sessions().map((s) => ({ label: s.name ?? `${s.term} ${s.year} ${s.campus}`.trim(), value: s.sessionId! })),
  );

  /** Course-request lines: primary courses first, then alternates. */
  protected readonly requestRows = computed<RequestRow[]>(() => {
    const req = this.request();
    if (!req) return [];
    const rows: RequestRow[] = [];
    (req.courses ?? []).forEach((r, i) => rows.push(this.toRequestRow(r, String(i + 1))));
    (req.alternatives ?? []).forEach((r, i) => rows.push(this.toRequestRow(r, `Alt ${i + 1}`)));
    return rows;
  });

  /** Flattened schedule rows: one per assigned class (or one per course when unassigned). */
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

  ngOnInit(): void {
    this.page.set('Scheduling Assistant');
    this.loadSessions();
  }

  private loadSessions(): void {
    this.loading.set(true);
    this.error.set(null);
    // sectioning=true -> only sessions that have online student scheduling enabled.
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
    return { online: true, sectioning: true, sessionId: this.sessionId ?? undefined };
  }

  onSessionChange(): void {
    this.load();
  }

  load(): void {
    if (this.sessionId == null) return;
    const cx = this.context();
    this.loadingData.set(true);
    this.error.set(null);
    this.searched.set(false);
    this.eligibility.set(null);
    this.request.set(null);
    this.schedule.set(null);

    // Eligibility gates the page and yields the status message; requests/schedule
    // are independent reads that may legitimately be empty for a new student.
    this.rpc.service<EligibilityCheck>(SERVICE, 'checkEligibility', [cx]).subscribe({
      next: (elig) => {
        this.eligibility.set(elig ?? null);
        // savedRequest / savedResult resolve a student server-side and throw
        // (NPE) when there is none — e.g. an administrator/advisor with no
        // student in this session. Only fetch the student's data when
        // eligibility resolved a studentId; otherwise the eligibility message
        // (rendered by the template) explains why there's nothing to show.
        if (elig?.studentId != null) {
          this.loadRequests(cx);
        } else {
          this.loadingData.set(false);
          this.searched.set(false);
        }
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  /** Eligibility resolved but no student is associated with this user/session. */
  protected readonly noStudent = computed(() => {
    const e = this.eligibility();
    return e != null && e.studentId == null;
  });

  private loadRequests(cx: StudentSectioningContext): void {
    this.rpc.service<CourseRequestInterface>(SERVICE, 'savedRequest', [cx]).subscribe({
      next: (req) => {
        this.request.set(req ?? null);
        this.loadSchedule(cx);
      },
      // A missing student is not fatal here — still try to show the schedule.
      error: () => this.loadSchedule(cx),
    });
  }

  private loadSchedule(cx: StudentSectioningContext): void {
    this.rpc.service<ClassAssignmentInterface>(SERVICE, 'savedResult', [cx]).subscribe({
      next: (res) => {
        this.schedule.set(res ?? null);
        this.searched.set(true);
        this.loadingData.set(false);
      },
      error: (e: ApiError) => {
        // Keep whatever eligibility/requests loaded; report the schedule error.
        this.error.set(e.message);
        this.searched.set(true);
        this.loadingData.set(false);
      },
    });
  }

  private toRequestRow(r: CourseRequestInterface_Request, priority: string): RequestRow {
    const parts = (r.requestedCourse ?? []).map((c) => this.requestedCourseLabel(c)).filter(Boolean);
    return {
      priority,
      courses: parts.join(' or '),
      waitList: !!r.waitList,
      status: this.requestedCourseStatus(r.requestedCourse?.[0]),
    };
  }

  private requestedCourseLabel(c?: RequestedCourse): string {
    if (!c) return '';
    if (c.freeTime?.length) return 'Free ' + c.freeTime.map((f) => this.freeTimeLabel(f)).join(', ');
    return [c.courseName, c.courseTitle].filter(Boolean).join(' - ');
  }

  private requestedCourseStatus(c?: RequestedCourse): string {
    return (c?.status ?? '').toString().replace(/_/g, ' ');
  }

  private courseHeaderRow(course: CourseAssignment, c: ClassAssignment | null, first = true): ScheduleRow {
    const courseName = [course.subject, course.courseNbr].filter(Boolean).join(' ');
    return {
      course: first ? courseName : '',
      title: first ? (course.title ?? '') : '',
      subpart: c?.subpart ?? '',
      section: c?.section ?? '',
      time: c ? this.classTime(c) : '',
      room: (c?.rooms ?? []).map((r) => r.value).filter(Boolean).join(', '),
      instructor: (c?.instructos ?? []).filter(Boolean).join(', '),
      credit: c?.credit ?? (first ? (course.creditText ?? '') : ''),
      note: c?.note ?? '',
      saved: !!c?.saved,
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
    const ampm = h >= 24 ? 'a' : h >= 12 ? 'p' : 'a';
    const hh = h > 12 ? h - 12 : h === 0 ? 12 : h;
    return `${hh}:${m < 10 ? '0' : ''}${m}${ampm}`;
  }

  private fail(e: ApiError, data = false): void {
    this.error.set(e.message);
    (data ? this.loadingData : this.loading).set(false);
  }
}
