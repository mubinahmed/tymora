import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSession,
  ApiError,
  CourseRequestInterface,
  CourseRequestInterface_Request,
  EligibilityCheck,
  RequestedCourse,
  StudentSectioningContext,
} from '../../core/models';

/** One display row derived from a CourseRequestInterface.Request priority slot. */
interface RequestRow {
  kind: 'Course' | 'Alternate';
  priority: number;
  /** Primary requested course label (requestedCourse[0]). */
  course: string;
  /** Alternative course labels for this priority (requestedCourse[1..]). */
  alternatives: string[];
  waitList: boolean;
  noSub: boolean;
  credit: string;
  status: string;
}

/**
 * Course Requests — the "requests" GWT page (StudentSectioningWidget in course-request
 * mode, CourseRequestInterface). The legacy screen is a large editor (course finder,
 * free-time entry, drag/drop priorities, wait-list & override validation, save/submit).
 *
 * This is a FUNCTIONAL CORE: it loads and displays the current student's SAVED course
 * requests, read-only. Backend is the classic SectioningService RemoteService
 * ("sectioning.gwt"): we mirror the widget's init sequence —
 *   1. ListAcademicSessions -> session picker (session-scoped access).
 *   2. checkEligibility(cx) -> resolves the logged-in student's id (cx.studentId).
 *   3. savedRequest(cx)     -> the CourseRequestInterface (courses + alternatives).
 * All three payloads are plain objects/arrays (no object-keyed maps), so the Gson
 * facade round-trips them cleanly.
 *
 * Deferred (see notes): editing/adding/removing courses, the course finder dialog,
 * free-time requests, drag-drop re-prioritisation, wait-list preferences, credit /
 * override validation, degree plans, advisor-request prepopulation, and save/submit.
 */
@Component({
  selector: 'app-course-requests',
  imports: [
    FormsModule,
    TableModule,
    TagModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './course-requests.html',
})
export class CourseRequests implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly sessions = signal<AcademicSession[]>([]);
  protected sessionId: number | null = null;

  protected readonly request = signal<CourseRequestInterface | null>(null);
  protected readonly courseRows = signal<RequestRow[]>([]);
  protected readonly alternateRows = signal<RequestRow[]>([]);

  ngOnInit(): void {
    this.page.set('Course Requests');
    this.loadSessions();
  }

  /** Load selectable academic sessions and default to the current (selected) one. */
  private loadSessions(): void {
    this.loading.set(true);
    this.rpc.execute<AcademicSession[]>('ListAcademicSessions', {}).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.selected) ?? sessions[0])?.uniqueId ?? null;
        this.loading.set(false);
        this.loadRequests();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onSessionChange(): void {
    this.request.set(null);
    this.courseRows.set([]);
    this.alternateRows.set([]);
    this.searched.set(false);
    this.loadRequests();
  }

  /** Resolve the student for the session, then load their saved course requests. */
  loadRequests(): void {
    if (this.sessionId == null) return;
    const cx: StudentSectioningContext = {
      online: true,
      sectioning: false,
      sessionId: this.sessionId,
    };
    this.loadingList.set(true);
    this.error.set(null);
    this.info.set(null);
    this.rpc.service<EligibilityCheck>('sectioning.gwt', 'checkEligibility', [cx]).subscribe({
      next: (check) => {
        if (check?.studentId == null) {
          this.loadingList.set(false);
          this.searched.set(true);
          this.info.set(
            check?.message ||
              'No student record is associated with your account for this academic session.',
          );
          return;
        }
        this.fetchSaved({ ...cx, studentId: check.studentId });
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  private fetchSaved(cx: StudentSectioningContext): void {
    this.rpc.service<CourseRequestInterface>('sectioning.gwt', 'savedRequest', [cx]).subscribe({
      next: (req) => {
        this.request.set(req ?? null);
        this.courseRows.set(this.toRows(req?.courses, 'Course'));
        this.alternateRows.set(this.toRows(req?.alternatives, 'Alternate'));
        this.searched.set(true);
        this.loadingList.set(false);
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  private toRows(requests: CourseRequestInterface_Request[] | undefined, kind: RequestRow['kind']): RequestRow[] {
    return (requests ?? []).map((r, i) => {
      const requested = r.requestedCourse ?? [];
      const [primary, ...alts] = requested;
      return {
        kind,
        priority: i + 1,
        course: this.courseLabel(primary),
        alternatives: alts.map((c) => this.courseLabel(c)).filter(Boolean),
        waitList: !!r.waitList,
        noSub: !!r.noSub,
        credit: this.creditLabel(primary),
        status: this.statusLabel(primary),
      };
    });
  }

  private courseLabel(c?: RequestedCourse): string {
    if (!c) return '';
    if (c.freeTime?.length) {
      const ft = c.freeTime
        .map((f) => `${(f.days ?? []).join('')} ${this.fmtTime(f.start)}-${this.fmtTime((f.start ?? 0) + (f.length ?? 0))}`)
        .join(', ');
      return `Free Time ${ft}`.trim();
    }
    return [c.courseName, c.courseTitle].filter(Boolean).join(' — ');
  }

  private creditLabel(c?: RequestedCourse): string {
    const credit = c?.credit ?? [];
    if (!credit.length) return '';
    if (credit.length === 1) return String(credit[0]);
    return `${Math.min(...credit)} – ${Math.max(...credit)}`;
  }

  private statusLabel(c?: RequestedCourse): string {
    return (c?.status ?? '').replace(/_/g, ' ');
  }

  /** Slot count (5-min slots from midnight) -> HH:MM. */
  private fmtTime(slot?: number): string {
    if (slot == null) return '';
    const min = slot * 5;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h}:${m.toString().padStart(2, '0')}`;
  }

  private fail(e: ApiError, list = false): void {
    this.error.set(e.message);
    (list ? this.loadingList : this.loading).set(false);
  }
}
