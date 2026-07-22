import { Component, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Curriculum Projection Rules (legacy curprojrules GWT page). Backed by the new
 * CurriculumProjectionRulesEditRequest command bean: LOAD returns a FLAT row
 * model (one row per academic area / major / classification tuple with
 * last-like enrollment and the stored projection fraction), SAVE writes the
 * edited projection numbers back per tuple.
 *
 * The legacy service returns an object-keyed nested HashMap the Gson command
 * facade cannot serialize, so this screen never calls it. Projection is stored
 * as a fraction (1.0 == 100%); it is edited here as a percentage. The legacy
 * page's classification-column combining, default-major inheritance and the
 * projected-student-count entry mode are display sugar and are intentionally
 * not reproduced (each tuple is edited directly).
 */

// --- request / response DTOs (inline; match Gson field naming iField -> field) ---
const DEFAULT_MAJOR_ID = -1;

interface ProjectionRuleRow {
  academicAreaId?: number;
  academicAreaCode?: string;
  academicAreaLabel?: string;
  majorId?: number;
  majorCode?: string;
  majorLabel?: string;
  classificationId?: number;
  classificationCode?: string;
  classificationLabel?: string;
  projection?: number | null;
  enrollment?: number | null;
}

type Operation = 'LOAD' | 'SAVE';

interface CurriculumProjectionRulesEditRequest {
  operation: Operation;
  rows?: ProjectionRuleRow[];
}

interface CurriculumProjectionRulesEditResponse {
  editable?: boolean;
  rows?: ProjectionRuleRow[];
}

/** Grid row: the DTO plus an editable percentage view of the projection. */
interface GridRow extends ProjectionRuleRow {
  projectionPercent: number | null;
}

@Component({
  selector: 'app-curriculum-projection-rules',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './curriculum-projection-rules.html',
})
export class CurriculumProjectionRules {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly editable = signal(false);
  protected readonly rows = signal<GridRow[]>([]);
  protected readonly count = computed(() => this.rows().length);

  constructor() {
    this.page.set('Curriculum Projection Rules');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc
      .execute<CurriculumProjectionRulesEditResponse>('CurriculumProjectionRulesEditRequest', {
        operation: 'LOAD',
      } as CurriculumProjectionRulesEditRequest)
      .subscribe({
        next: (res) => {
          this.apply(res);
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  private apply(res: CurriculumProjectionRulesEditResponse): void {
    this.editable.set(!!res.editable);
    this.rows.set(
      (res.rows ?? []).map((r) => ({
        ...r,
        projectionPercent: r.projection == null ? null : Math.round(r.projection * 1000) / 10,
      })),
    );
  }

  isDefaultMajor(r: GridRow): boolean {
    return r.majorId === DEFAULT_MAJOR_ID;
  }

  majorDisplay(r: GridRow): string {
    return this.isDefaultMajor(r) ? r.majorLabel ?? 'Default' : `${r.majorCode ?? ''} — ${r.majorLabel ?? ''}`;
  }

  /** Projected students = projection fraction * last-like enrollment. */
  projected(r: GridRow): number | null {
    if (r.projectionPercent == null || r.enrollment == null) return null;
    return Math.round((r.projectionPercent / 100) * r.enrollment);
  }

  save(): void {
    if (!this.editable()) return;
    const payload: ProjectionRuleRow[] = this.rows().map((r) => ({
      academicAreaId: r.academicAreaId,
      majorId: r.majorId,
      classificationId: r.classificationId,
      projection: r.projectionPercent == null ? null : r.projectionPercent / 100,
      enrollment: r.enrollment,
    }));

    this.saving.set(true);
    this.rpc
      .execute<CurriculumProjectionRulesEditResponse>('CurriculumProjectionRulesEditRequest', {
        operation: 'SAVE',
        rows: payload,
      } as CurriculumProjectionRulesEditRequest)
      .subscribe({
        next: (res) => {
          this.apply(res);
          this.saving.set(false);
          this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Curriculum projection rules saved.' });
        },
        error: (e: ApiError) => {
          this.saving.set(false);
          this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
        },
      });
  }
}
