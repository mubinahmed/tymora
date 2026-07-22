import { Component, OnInit, computed, inject, signal } from '@angular/core';
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

/** One selectable examination type (UnassignedExamsInterface.ExamTypeInfo). */
interface ExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected exam row (SimpleListInterface.Row). */
interface ExamRow {
  id?: number;
  cells?: string[];
}

/** Response of the UnassignedExamsRequest command bean. */
interface UnassignedExamsResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: ExamTypeInfo[];
  columns?: string[];
  rows?: ExamRow[];
}

/** Request payload for the UnassignedExamsRequest command bean. */
interface UnassignedExamsRequest {
  examTypeId?: number;
}

/**
 * Read-only listing for the legacy unassignedExams.action (Not-Assigned
 * Examinations) page. Pick an examination type; the backend returns the exams
 * of that type that have no committed period assignment, projected to string
 * rows (Examination, Length, Seating Type, Size, Max Rooms, Instructor).
 * Served by the new UnassignedExamsBackend command bean; gated by
 * Right.Examinations + Right.NotAssignedExaminations for the current academic
 * session. Editing, preference detail and PDF/CSV export remain on the legacy
 * page (deferred).
 */
@Component({
  selector: 'app-unassigned-exams',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './unassigned-exams.html',
})
export class UnassignedExams implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<UnassignedExamsResponse | null>(null);
  protected readonly filter = signal('');

  protected examTypeId: number | null = null;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<ExamRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Not-Assigned Examinations');
    this.load();
  }

  onExamTypeChange(): void {
    this.load();
  }

  reload(): void {
    this.load();
  }

  exportCsv(): void {
    const rows = this.rows().map((r) => (r.cells ?? []).map((c) => c ?? ''));
    downloadCsv(this.data()?.title || 'Not-Assigned Examinations', this.columns(), rows);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: UnassignedExamsRequest = {};
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<UnassignedExamsResponse>('UnassignedExamsRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Not-Assigned Examinations');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
