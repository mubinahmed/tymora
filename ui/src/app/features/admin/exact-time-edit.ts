import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Exact Time Pattern editor (legacy Struts exactTimeEdit page). Backed by the
 * ExactTimeEditRequest command bean: LOAD returns every ExactTimeMins row (the
 * display-only minutes-per-meeting range plus the two editable fields — number
 * of time slots per meeting and break time), SAVE writes those two fields back.
 * The legacy page never added or removed rows, so this screen only edits.
 */

// --- request / response DTOs (inline; match Gson field naming iField -> field) ---
type Operation = 'LOAD' | 'SAVE';

interface ExactTimeMinsRecord {
  id?: number | null;
  minsPerMtgMin?: number;
  minsPerMtgMax?: number;
  nrSlots?: number;
  breakTime?: number;
}

interface ExactTimeEditRequest {
  operation: Operation;
  records?: ExactTimeMinsRecord[];
}

interface ExactTimeEditResponse {
  editable?: boolean;
  records?: ExactTimeMinsRecord[];
}

@Component({
  selector: 'app-exact-time-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputNumberModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exact-time-edit.html',
})
export class ExactTimeEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly records = signal<ExactTimeMinsRecord[]>([]);
  protected readonly editable = signal(false);

  constructor() {
    this.page.set('Exact Time Pattern');
    this.reload();
  }

  rangeLabel(r: ExactTimeMinsRecord): string {
    if (!r.minsPerMtgMax) return '0';
    return `${r.minsPerMtgMin} .. ${r.minsPerMtgMax}`;
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ExactTimeEditResponse>('ExactTimeEditRequest', { operation: 'LOAD' }).subscribe({
      next: (res) => {
        this.records.set(res.records ?? []);
        this.editable.set(!!res.editable);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    const records = this.records().map((r) => ({
      id: r.id,
      nrSlots: r.nrSlots ?? 0,
      breakTime: r.breakTime ?? 0,
    }));
    this.saving.set(true);
    const request: ExactTimeEditRequest = { operation: 'SAVE', records };
    this.rpc.execute<ExactTimeEditResponse>('ExactTimeEditRequest', request).subscribe({
      next: (res) => {
        this.records.set(res.records ?? []);
        this.editable.set(!!res.editable);
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Exact time pattern updated.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
