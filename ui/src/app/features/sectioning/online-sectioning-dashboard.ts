import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  CodeLabel,
  ClassAssignmentInterface_StudentInfo,
  EnrollmentInfo,
  SectioningProperties,
  SectioningStatusFilterRpcRequest,
  Student,
  StudentStatusInfo,
} from '../../core/models';

/**
 * Online Sectioning Dashboard (legacy GWT SectioningStatusPage, key "onlinesctdash",
 * i.e. new SectioningStatusPage(online=true)).
 *
 * Backend is the classic SectioningService RemoteService (@RemoteServiceRelativePath
 * "sectioning.gwt"), reached through the /api/service facade:
 *  - getProperties(null)                              -> SectioningProperties (status card)
 *  - lookupStudentSectioningStates()                  -> StudentStatusInfo[] (reference)
 *  - findEnrollmentInfos(online, query, filter, null) -> EnrollmentInfo[]  (Courses tab)
 *  - findStudentInfos(online, query, filter)          -> StudentInfo[]     (Students tab)
 *
 * The academic session is taken from the server-side "status page session" (the user's
 * current session); these methods carry no sessionId argument, so none is sent.
 *
 * The result lists begin with a header row and end with a totals footer row (both have
 * a null courseId / null student); we drop those and render the data rows.
 *
 * Deferred vs. the GWT original: the rich SectioningStatusFilterBox (replaced by a plain
 * free-text query), the Change Log tab, per-course/per-student enrollment drill-down
 * dialogs, student selection + bulk operations (email / mass cancel / status / note /
 * reload), CSV/XLS export, column sort/hide/show, client pagination, and URL history.
 */
@Component({
  selector: 'app-online-sectioning-dashboard',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    CardModule,
    InputTextModule,
    SelectButtonModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './online-sectioning-dashboard.html',
  styles: [
    `
      .osd-center { display: flex; justify-content: center; padding: 2rem; }
      .osd-mt { margin-top: 1rem; }
      .osd-mb { margin-bottom: 0.5rem; }
      .osd-muted { color: var(--text-color-secondary); font-size: 0.875rem; }
      .osd-status { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; margin-bottom: 1rem; }
      .osd-filter { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: center; }
      .osd-num { text-align: right; }
    `,
  ],
})
export class OnlineSectioningDashboard implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  private static readonly SERVICE = 'sectioning.gwt';
  private readonly online = true;

  protected readonly loading = signal(true);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly properties = signal<SectioningProperties | null>(null);
  protected readonly states = signal<StudentStatusInfo[]>([]);

  protected readonly courses = signal<EnrollmentInfo[]>([]);
  protected readonly students = signal<ClassAssignmentInterface_StudentInfo[]>([]);

  /** free-text filter (the legacy SectioningStatusFilterBox is deferred) */
  protected query = '';

  protected view: 'courses' | 'students' = 'courses';
  protected readonly viewOptions = [
    { label: 'Courses', value: 'courses', icon: 'pi pi-book' },
    { label: 'Students', value: 'students', icon: 'pi pi-users' },
  ];

  /** Data rows only (drop the leading header row and trailing totals footer). */
  protected readonly courseRows = computed<EnrollmentInfo[]>(() =>
    this.courses().filter((c) => c.courseId != null),
  );
  protected readonly studentRows = computed<ClassAssignmentInterface_StudentInfo[]>(() =>
    this.students().filter((s) => s.student != null),
  );

  ngOnInit(): void {
    this.page.set('Online Sectioning Dashboard');
    this.loadProperties();
  }

  private loadProperties(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc
      .service<SectioningProperties>(OnlineSectioningDashboard.SERVICE, 'getProperties', [null])
      .subscribe({
        next: (props) => {
          this.properties.set(props ?? {});
          this.loadStates();
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  private loadStates(): void {
    this.rpc
      .service<StudentStatusInfo[]>(OnlineSectioningDashboard.SERVICE, 'lookupStudentSectioningStates', [])
      .subscribe({
        next: (list) => {
          this.states.set(list ?? []);
          this.loading.set(false);
        },
        // States are reference data only; a failure here should not block the page.
        error: () => this.loading.set(false),
      });
  }

  onViewChange(): void {
    if (this.searched()) this.search();
  }

  search(): void {
    const query = this.query.trim();
    const filter: SectioningStatusFilterRpcRequest = { command: 'ENUMERATE', text: query, options: {} };
    this.searching.set(true);
    this.error.set(null);

    if (this.view === 'courses') {
      this.rpc
        .service<EnrollmentInfo[]>(OnlineSectioningDashboard.SERVICE, 'findEnrollmentInfos', [
          this.online,
          query,
          filter,
          null,
        ])
        .subscribe({
          next: (list) => this.done(() => this.courses.set(list ?? [])),
          error: (e: ApiError) => this.fail(e),
        });
    } else {
      this.rpc
        .service<ClassAssignmentInterface_StudentInfo[]>(
          OnlineSectioningDashboard.SERVICE,
          'findStudentInfos',
          [this.online, query, filter],
        )
        .subscribe({
          next: (list) => this.done(() => this.students.set(list ?? [])),
          error: (e: ApiError) => this.fail(e),
        });
    }
  }

  private done(apply: () => void): void {
    apply();
    this.searched.set(true);
    this.searching.set(false);
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.searching.set(false);
  }

  studentName(s: Student | undefined): string {
    return s?.name ?? '';
  }

  codes(list: CodeLabel[] | undefined): string {
    return (list ?? []).map((c) => c.code ?? c.label ?? '').filter(Boolean).join(', ');
  }
}
