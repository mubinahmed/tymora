import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';

/** One selectable examination type (ExamRoomAvailabilityInterface.ExamTypeInfo). */
interface ExamTypeInfo {
  id?: number;
  label?: string;
}

/** A projected report row (SimpleListInterface.Row). */
interface ReportRow {
  id?: number;
  cells?: string[];
}

/** Response of the ExamRoomAvailabilityRequest command bean. */
interface ExamRoomAvailabilityResponse {
  title?: string;
  examTypeId?: number;
  compare?: boolean;
  serviceAvailable?: boolean;
  timestamp?: string;
  warning?: string;
  examTypes?: ExamTypeInfo[];
  columns?: string[];
  rows?: ReportRow[];
}

/** Request payload for the ExamRoomAvailabilityRequest command bean. */
interface ExamRoomAvailabilityRequest {
  examTypeId?: number;
  filter?: string;
  includeExams?: boolean;
  compare?: boolean;
  refresh?: boolean;
}

/**
 * Read-only report for the legacy roomAvailability.action (Examination Room
 * Availability) page. Pick an examination type and optionally a room-name
 * filter; the backend returns the external room-availability time blocks that
 * overlap the exam periods (availability mode) or those blocks matched against
 * the committed exam assignments to surface mismatches (compare mode). Served
 * by the new ExamRoomAvailabilityBackend command bean; gated by
 * Right.RoomAvailability for the current academic session. When no external
 * room-availability service is configured the page shows a "nothing to display"
 * notice, mirroring the legacy behaviour. PDF export and HTML mismatch
 * highlighting remain on the legacy page (CSV export is provided client-side).
 */
@Component({
  selector: 'app-exam-room-availability',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-room-availability.html',
})
export class ExamRoomAvailability implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamRoomAvailabilityResponse | null>(null);
  protected readonly nameFilter = signal('');

  protected examTypeId: number | null = null;
  protected roomFilter = '';
  protected includeExams = false;
  protected compare = false;

  protected readonly examTypeOptions = computed(() =>
    (this.data()?.examTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<ReportRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.nameFilter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Examination Room Availability');
    this.load();
  }

  apply(): void {
    this.load();
  }

  refresh(): void {
    this.load(true);
  }

  exportCsv(): void {
    const rows = this.rows().map((r) => (r.cells ?? []).map((c) => c ?? ''));
    downloadCsv(this.data()?.title || 'Room Availability', this.columns(), rows);
  }

  private load(refresh = false): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamRoomAvailabilityRequest = {
      filter: this.roomFilter,
      includeExams: this.includeExams,
      compare: this.compare,
      refresh,
    };
    if (this.examTypeId != null) request.examTypeId = this.examTypeId;
    this.rpc.execute<ExamRoomAvailabilityResponse>('ExamRoomAvailabilityRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.examTypeId = d.examTypeId ?? null;
        this.page.set(d.title || 'Examination Room Availability');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
