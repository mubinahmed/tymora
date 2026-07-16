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

/** One selectable examination type (ExamListInterface.ExamTypeInfo). */
interface ExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected exam row (SimpleListInterface.Row). */
interface ExamRow {
  id?: number;
  cells?: string[];
}

/** Response of the ExamListRequest command bean (ExamListInterface.ExamListResponse). */
interface ExamListResponse {
  title?: string;
  examTypeId?: number;
  examTypes?: ExamTypeInfo[];
  columns?: string[];
  rows?: ExamRow[];
}

/** Request payload for the ExamListRequest command bean. */
interface ExamListRequest {
  examTypeId?: number;
}

/**
 * Read-only listing for the legacy examList.action (Examinations) page. Pick an
 * examination type; the backend returns the exams of that type projected to
 * string rows (Examination, Length, Seating Type, Size, Max Rooms, Instructor,
 * Assigned Period, Assigned Room). Served by the new ExamListBackend command
 * bean; gated by Right.Examinations for the current academic session. Editing
 * and PDF/CSV export remain on the legacy page (deferred).
 */
@Component({
  selector: 'app-exams-list',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exams-list.html',
})
export class ExamsList implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamListResponse | null>(null);
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
    this.page.set('Examinations');
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
    const request: ExamListRequest = {};
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamListResponse>('ExamListRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Examinations');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
