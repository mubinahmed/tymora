import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/** Comparison mode (ExamChangesInterface.Mode). */
type ExamChangeMode = 'Initial' | 'Best';

/** One changed examination (ExamChangesInterface.ExamChangeRow). */
interface ExamChangeRow {
  examId?: number;
  exam?: string;
  fromPeriod?: string;
  toPeriod?: string;
  fromRoom?: string;
  toRoom?: string;
  seatingType?: string;
  students?: number;
  instructor?: string;
  directConflicts?: number;
  moreThanTwoADayConflicts?: number;
  backToBackConflicts?: number;
}

/** Response of the ExamChangesRequest command bean (ExamChangesInterface.ExamChangesResponse). */
interface ExamChangesResponse {
  solverLoaded?: boolean;
  message?: string;
  title?: string;
  examTypeId?: number;
  examTypeLabel?: string;
  mode?: ExamChangeMode;
  rows?: ExamChangeRow[];
}

/** Request payload for the ExamChangesRequest command bean. */
interface ExamChangesRequest {
  examTypeId?: number;
  mode?: ExamChangeMode;
  subjectAreaId?: number;
}

/**
 * Read-only view of the legacy examChanges.action (Examination Assignment
 * Changes) page. The data is read from the in-memory examination solver: for
 * each exam whose current assignment differs from the reference solution, the
 * period and room(s) are shown as from -> to with the current conflict
 * counters. A toggle switches the reference between the Initial (input) and
 * the Best solution. When no exam solver is loaded, a "solver not loaded"
 * banner is shown (mirroring the course-solver screens). Served by the new
 * ExamChangesBackend command bean; gated by Right.ExaminationAssignmentChanges
 * for the current academic session. The interactive suggestions dialog, the
 * conflict delta decorations and PDF/CSV export remain on the legacy page.
 */
@Component({
  selector: 'app-exam-changes',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectButtonModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-changes.html',
})
export class ExamChanges implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamChangesResponse | null>(null);
  protected readonly filter = signal('');

  protected readonly modeOptions = [
    { label: 'Changes vs. Initial', value: 'Initial' as ExamChangeMode },
    { label: 'Changes vs. Best', value: 'Best' as ExamChangeMode },
  ];
  protected mode: ExamChangeMode = 'Initial';

  protected readonly solverLoaded = computed(() => this.data()?.solverLoaded === true);

  protected readonly rows = computed<ExamChangeRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) =>
      [r.exam, r.fromPeriod, r.toPeriod, r.fromRoom, r.toRoom, r.instructor]
        .filter((v) => v != null)
        .join(' ')
        .toLowerCase()
        .includes(f),
    );
  });

  ngOnInit(): void {
    this.page.set('Examination Assignment Changes');
    this.load();
  }

  onModeChange(): void {
    this.load();
  }

  reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamChangesRequest = { mode: this.mode };
    this.rpc.execute<ExamChangesResponse>('ExamChangesRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.mode) this.mode = d.mode;
        this.page.set(d.title || 'Examination Assignment Changes');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
