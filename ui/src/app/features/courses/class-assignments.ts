import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';

/**
 * Read-only Class Assignments report (legacy classAssignmentsReportSearch.action),
 * served by the additive ClassAssignmentsReportBackend command bean. One request
 * drives two modes:
 *   - subjectAreaId omitted -> subject-area picker rows (loaded on init)
 *   - subjectAreaId set      -> committed class-assignment rows for that subject
 *                               area (+ optional course number)
 * Both share the SimpleListResponse {columns[], rows:[{id, cells[]}]} shape.
 */

interface SimpleListRow {
  id?: number;
  cells?: string[];
}
interface SimpleListResponse {
  title?: string;
  columns?: string[];
  rows?: SimpleListRow[];
}
interface ClassAssignmentsReportRequest {
  subjectAreaId?: number;
  courseNbr?: string;
}
interface SubjectOption {
  id: number;
  label: string;
}

@Component({
  selector: 'app-class-assignments',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './class-assignments.html',
})
export class ClassAssignments {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly subjects = signal<SubjectOption[]>([]);
  protected readonly subjectAreaId = signal<number | null>(null);
  protected readonly courseNbr = signal<string>('');

  protected readonly loadingSubjects = signal(true);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly result = signal<SimpleListResponse | null>(null);
  protected readonly columns = computed<string[]>(() => this.result()?.columns ?? []);
  protected readonly rows = computed<SimpleListRow[]>(() => this.result()?.rows ?? []);

  constructor() {
    this.page.set('Class Assignments');
    this.loadSubjects();
  }

  private loadSubjects(): void {
    this.loadingSubjects.set(true);
    this.error.set(null);
    // subjectAreaId omitted -> backend returns the subject-area picker rows.
    this.rpc
      .execute<SimpleListResponse>('ClassAssignmentsReportRequest', {} as ClassAssignmentsReportRequest)
      .subscribe({
        next: (d) => {
          const opts = (d.rows ?? [])
            .filter((r) => r.id != null)
            .map((r) => ({ id: r.id as number, label: (r.cells ?? [''])[0] ?? '' }));
          this.subjects.set(opts);
          this.loadingSubjects.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loadingSubjects.set(false);
        },
      });
  }

  search(): void {
    const sid = this.subjectAreaId();
    if (sid == null) return;
    this.searching.set(true);
    this.error.set(null);
    const req: ClassAssignmentsReportRequest = { subjectAreaId: sid };
    const nbr = this.courseNbr().trim();
    if (nbr) req.courseNbr = nbr;
    this.rpc.execute<SimpleListResponse>('ClassAssignmentsReportRequest', req).subscribe({
      next: (d) => {
        this.result.set(d);
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.searching.set(false);
      },
    });
  }

  exportCsv(): void {
    const rows = this.rows().map((r) => (r.cells ?? []).map((c) => c ?? ''));
    downloadCsv(this.result()?.title || 'Class Assignments', this.columns(), rows);
  }
}
