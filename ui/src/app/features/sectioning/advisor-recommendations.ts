import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSessionInfo,
  AdvisingStudentDetails,
  ApiError,
  CourseRequestInterface,
  CourseRequestInterface_Request,
  RequestedCourse,
} from '../../core/models';

const SERVICE = 'sectioning.gwt';

/** A flattened course-recommendation / request line for the tables. */
interface RecRow {
  priority: string;
  courses: string;
  credit: string;
  note: string;
  waitList: boolean;
  critical: string;
  status: string;
}

/**
 * Advisor Course Recommendations (legacy AdvisorCourseRequestsPage, page key "acrf")
 * — classic RemoteService screen via /api/service/sectioning.gwt.
 *
 * Functional core: look up a student by their external id (getStudentSessions),
 * pick one of the student's academic sessions, then load the advising record
 * (getStudentAdvisingDetails) — student identity/contact, advisor email, status,
 * credit note — plus two read-only tables: the advisor's recommended courses and
 * the student's own course requests.
 *
 * Deferred vs. the GWT original: the people-lookup dialog (external id is typed
 * here), interactive recommendation entry with the course finder, alternatives
 * editing, degree plans, PIN release, last-notes dialog, credit-hours computation,
 * other-session recommendations, and submit / export-PDF / email actions. This is a
 * read-only view of the current advising record.
 */
@Component({
  selector: 'app-advisor-recommendations',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
  ],
  templateUrl: './advisor-recommendations.html',
})
export class AdvisorRecommendations implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected studentId = '';

  protected readonly loading = signal(false);
  protected readonly loadingData = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly sessions = signal<AcademicSessionInfo[]>([]);
  protected sessionId: number | null = null;

  protected readonly details = signal<AdvisingStudentDetails | null>(null);

  protected readonly sessionOptions = computed(() =>
    this.sessions().map((s) => ({
      label: s.name ?? `${s.term ?? ''} ${s.year ?? ''} ${s.campus ?? ''}`.trim(),
      value: s.sessionId!,
    })),
  );

  /** Advisor recommendations: primary courses first, then alternates. */
  protected readonly advisorRows = computed<RecRow[]>(() => this.toRows(this.details()?.request));

  /** The student's own course requests. */
  protected readonly studentRows = computed<RecRow[]>(() => this.toRows(this.details()?.studentRequest));

  protected readonly creditNote = computed<string>(() => this.details()?.request?.creditNote ?? '');

  ngOnInit(): void {
    this.page.set('Advisor Course Recommendations');
  }

  /** Step 1: resolve the student's external id to the sessions they exist in. */
  lookup(): void {
    const ext = this.studentId.trim();
    if (!ext) return;
    this.loading.set(true);
    this.error.set(null);
    this.searched.set(false);
    this.details.set(null);
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

  /** Step 2: load the advising record for the selected student + session. */
  private loadDetails(): void {
    const ext = this.studentId.trim();
    if (!ext || this.sessionId == null) return;
    this.loadingData.set(true);
    this.error.set(null);
    this.searched.set(false);
    this.details.set(null);
    this.rpc
      .service<AdvisingStudentDetails>(SERVICE, 'getStudentAdvisingDetails', [this.sessionId, ext])
      .subscribe({
        next: (d) => {
          this.details.set(d ?? null);
          this.searched.set(true);
          this.loadingData.set(false);
        },
        error: (e: ApiError) => this.fail(e, true),
      });
  }

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
    const credit = r.advisorCredit ?? this.creditRange(first);
    return {
      priority,
      courses,
      credit,
      note: r.advisorNote ?? '',
      waitList: !!r.waitList,
      critical: this.criticalLabel(r.critical),
      status: (first?.status ?? '').toString().replace(/_/g, ' '),
    };
  }

  private courseLabel(c?: RequestedCourse): string {
    if (!c) return '';
    if (c.freeTime?.length) return 'Free Time';
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
        return '';
    }
  }

  private fail(e: ApiError, data = false): void {
    this.error.set(e.message);
    (data ? this.loadingData : this.loading).set(false);
  }
}
