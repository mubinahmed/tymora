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

/** A projected change-log row (reuses SimpleListInterface.Row). */
interface ChangeLogRow {
  id?: number;
  cells?: string[];
}

/** Response of the SessionChangeLogRequest bean (reuses SimpleListInterface.SimpleListResponse). */
interface ChangeLogResponse {
  title?: string;
  columns?: string[];
  rows?: ChangeLogRow[];
}

/** Request payload for the SessionChangeLogRequest command bean. */
interface ChangeLogRequest {
  limit?: number;
}

/**
 * Read-only "Last Changes" (Change Log) report — the Angular replacement for the
 * legacy Struts lastChanges.action (LastChangesAction). Lists the most recent
 * ChangeLog entries for the current academic session, projected to string rows
 * (Date, Department, Subject, Manager, Page, Object, Operation). Served by the new
 * SessionChangeLogBackend command bean; gated by Right.LastChanges.
 *
 * A recent-N selector caps how many entries are fetched. Manager/subject/department
 * filtering and PDF export from the legacy page are intentionally deferred — a
 * client-side text filter covers most triage needs.
 */
@Component({
  selector: 'app-change-log',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './change-log.html',
})
export class ChangeLog implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ChangeLogResponse | null>(null);
  protected readonly filter = signal('');

  protected limit = 100;
  protected readonly limitOptions = [
    { label: 'Last 100', value: 100 },
    { label: 'Last 250', value: 250 },
    { label: 'Last 500', value: 500 },
    { label: 'Last 1000', value: 1000 },
  ];

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<ChangeLogRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Last Changes');
    this.load();
  }

  onLimitChange(): void {
    this.load();
  }

  reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ChangeLogRequest = { limit: this.limit };
    this.rpc.execute<ChangeLogResponse>('SessionChangeLogRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.page.set(d.title || 'Last Changes');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
