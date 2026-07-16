import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
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
} from '../../core/models';

const SERVICE = 'sectioning.gwt';

type View = 'courses' | 'students';

/**
 * Batch Sectioning Dashboard — the batch (offline solver) variant of the legacy
 * GWT SectioningStatusPage (`SectioningStatusPage(false)`). Backed by the classic
 * SectioningService RemoteService (@RemoteServiceRelativePath "sectioning.gwt")
 * via /api/service.
 *
 * Batch mode always calls the service with online=false, which reads the currently
 * loaded student solver (getStudentSolver()); if no student solver is loaded the
 * backend throws "no solver" and we surface it as an error.
 *
 * Functional core: the two primary dashboard tables, toggled by a view switch,
 * driven by an optional filter query:
 *   - Courses/enrollments  -> findEnrollmentInfos(false, query, null, null) -> EnrollmentInfo[]
 *   - Students             -> findStudentInfos(false, query, null)          -> StudentInfo[]
 * Properties are loaded once (getProperties(null)) to gate actions.
 *
 * Deferred (see notes): the rich SectioningStatusFilterBox query builder (we send a
 * plain text query, filter=null), the hierarchical course/config/subpart/class tree
 * expansion, per-student enrollment detail dialog, change log tab, reservations, and
 * all mutating batch actions (change status, mass cancel, reload/request update,
 * override checks, email, group/pin management).
 */
@Component({
  selector: 'app-batch-sectioning-dashboard',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectButtonModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './batch-sectioning-dashboard.html',
})
export class BatchSectioningDashboard implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly properties = signal<SectioningProperties | null>(null);
  protected readonly courses = signal<EnrollmentInfo[]>([]);
  protected readonly students = signal<ClassAssignmentInterface_StudentInfo[]>([]);

  protected query = '';
  protected readonly view = signal<View>('courses');
  protected readonly viewOptions = [
    { label: 'Courses', value: 'courses' as View, icon: 'pi pi-book' },
    { label: 'Students', value: 'students' as View, icon: 'pi pi-users' },
  ];

  ngOnInit(): void {
    this.page.set('Batch Sectioning Dashboard');
    // Best-effort: properties gate mutating actions; a failure here should not block
    // the (independently permissioned) status tables, so it is swallowed.
    this.rpc.service<SectioningProperties>(SERVICE, 'getProperties', [null]).subscribe({
      next: (p) => this.properties.set(p ?? null),
      error: () => this.properties.set(null),
    });
    this.load();
  }

  onViewChange(v: View): void {
    this.view.set(v);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const q = this.query.trim();
    if (this.view() === 'courses') {
      this.rpc.service<EnrollmentInfo[]>(SERVICE, 'findEnrollmentInfos', [false, q, null, null]).subscribe({
        next: (list) => this.done(() => this.courses.set(list ?? [])),
        error: (e: ApiError) => this.fail(e),
      });
    } else {
      this.rpc.service<ClassAssignmentInterface_StudentInfo[]>(SERVICE, 'findStudentInfos', [false, q, null]).subscribe({
        next: (list) => this.done(() => this.students.set(list ?? [])),
        error: (e: ApiError) => this.fail(e),
      });
    }
  }

  private done(apply: () => void): void {
    apply();
    this.searched.set(true);
    this.loading.set(false);
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.courses.set([]);
    this.students.set([]);
    this.searched.set(true);
    this.loading.set(false);
  }

  /** Course rows carry a level (0=course, 1=config, 2=subpart, 3=class); label the row. */
  courseName(r: EnrollmentInfo): string {
    if (r.subject || r.courseNbr) return [r.subject, r.courseNbr].filter(Boolean).join(' ');
    if (r.clazz) return r.clazz;
    if (r.subpart) return r.subpart;
    if (r.config) return r.config;
    return '';
  }

  codes(list?: CodeLabel[]): string {
    return (list ?? []).map((c) => c.code || c.label).filter(Boolean).join(', ');
  }
}
