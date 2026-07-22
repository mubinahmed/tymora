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

/** One selectable examination type (ExamTimetableGridInterface.ExamTypeInfo). */
interface ExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected grid row (SimpleListInterface.Row). */
interface GridRow {
  id?: number;
  cells?: string[];
}

/** Response of the ExamTimetableGridRequest command bean. */
interface ExamTimetableGridResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: ExamTypeInfo[];
  columns?: string[];
  rows?: GridRow[];
}

/** Request payload for the ExamTimetableGridRequest command bean. */
interface ExamTimetableGridRequest {
  examTypeId?: number;
}

/**
 * Read-only view of the legacy examGrid.action (Examination Timetable) page.
 * The legacy page draws a colored pixel time-grid of periods by resource whose
 * cells come from the in-memory solver or, when no matching solver is loaded,
 * from the persisted committed assignment. This screen surfaces only the
 * persisted assignment as a period-by-room table (one row per exam+room cell,
 * ordered by period). Served by the new ExamTimetableGridBackend command bean;
 * gated by Right.Examinations + Right.ExaminationTimetable for the current
 * academic session. The solver-driven pixel grid, alternate resources /
 * conflict backgrounds, and PDF export remain on the legacy page (deferred).
 */
@Component({
  selector: 'app-exam-grid',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-grid.html',
})
export class ExamGrid implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamTimetableGridResponse | null>(null);
  protected readonly filter = signal('');

  protected examTypeId: number | null = null;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<GridRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Examination Timetable');
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
    downloadCsv(this.data()?.title || 'Examination Timetable', this.columns(), rows);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamTimetableGridRequest = {};
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamTimetableGridResponse>('ExamTimetableGridRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Examination Timetable');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
