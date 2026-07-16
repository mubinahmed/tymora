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

/** One selectable examination type (ExamPeriodListInterface.ExamTypeInfo). */
interface ExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected examination-period row (SimpleListInterface.Row). */
interface ExamPeriodRow {
  id?: number;
  cells?: string[];
}

/** Response of the ExamPeriodListRequest command bean (ExamPeriodListInterface.ExamPeriodListResponse). */
interface ExamPeriodListResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: ExamTypeInfo[];
  columns?: string[];
  rows?: ExamPeriodRow[];
}

/** Request payload for the ExamPeriodListRequest command bean. */
interface ExamPeriodListRequest {
  examTypeId?: number;
}

/**
 * Read-only listing for the legacy examPeriodEdit.action (Examination Periods)
 * page. Optionally pick an examination type; the backend returns the
 * examination periods of the current academic session projected to string rows
 * (Type, Date, Start Time, End Time, Exam Length, Event Start Offset, Event Stop
 * Offset, Preference). Served by the new ExamPeriodListBackend command bean;
 * gated by Right.ExaminationPeriods for the current academic session. Add / Edit
 * / Delete and the auto-setup wizard remain on the legacy page (deferred).
 */
@Component({
  selector: 'app-exam-periods',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-periods.html',
})
export class ExamPeriods implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamPeriodListResponse | null>(null);
  protected readonly filter = signal('');

  protected examTypeId: number | null = null;

  protected readonly examTypeOptions = computed(() => [
    { label: 'All Types', value: null as number | null },
    ...(this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: (t.id ?? null) as number | null })),
  ]);

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<ExamPeriodRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Examination Periods');
    this.load();
  }

  onExamTypeChange(): void {
    this.load();
  }

  reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamPeriodListRequest = {};
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamPeriodListResponse>('ExamPeriodListRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Examination Periods');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
