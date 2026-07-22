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

/** One selectable examination type (AssignedExamsInterface.AssignedExamTypeInfo). */
interface AssignedExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected exam row (SimpleListInterface.Row). */
interface AssignedExamRow {
  id?: number;
  cells?: string[];
}

/** Response of the AssignedExamsRequest command bean (AssignedExamsInterface.AssignedExamsResponse). */
interface AssignedExamsResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: AssignedExamTypeInfo[];
  columns?: string[];
  rows?: AssignedExamRow[];
}

/** Request payload for the AssignedExamsRequest command bean. */
interface AssignedExamsRequest {
  examTypeId?: number;
}

/**
 * Read-only listing for the legacy assignedExams.action (Assigned
 * Examinations) page. Pick an examination type; the backend returns the exams
 * of that type that have a committed assigned period, projected to string rows
 * (Examination, Type, Length, Seating, Size, Assigned Period, Assigned
 * Room(s), Instructor). Served by the new AssignedExamsBackend command bean
 * from persisted data; gated by Right.AssignedExaminations for the current
 * academic session. Conflict counters and PDF/CSV export remain on the legacy
 * page (deferred).
 */
@Component({
  selector: 'app-assigned-exams',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './assigned-exams.html',
})
export class AssignedExams implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<AssignedExamsResponse | null>(null);
  protected readonly filter = signal('');

  protected examTypeId: number | null = null;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<AssignedExamRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Assigned Examinations');
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
    downloadCsv(this.data()?.title || 'Assigned Examinations', this.columns(), rows);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: AssignedExamsRequest = {};
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<AssignedExamsResponse>('AssignedExamsRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Assigned Examinations');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
