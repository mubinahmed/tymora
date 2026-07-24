import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, SimpleListResponse, SimpleListRow } from '../../core/models';
import { downloadCsv } from '../../core/csv';

/** For list pages that have a dedicated Angular editor: the Add target + row-edit base. */
interface Editor {
  createLabel: string;
  createLink: unknown[];
  editBase: string;
}

/**
 * Generic listing for legacy Struts admin pages served by the SimpleListBackend
 * command bean. Routed as /list/:page — the :page key selects the entity list
 * (sessions, managers, solverGroups, …). Columns and rows come from the backend.
 *
 * Pages that have a dedicated Angular create/edit screen (see {@link AdminList.EDITORS})
 * additionally get an Add button and clickable rows that route to those editors;
 * pages without an editor stay read-only.
 */
@Component({
  selector: 'app-admin-list',
  imports: [RouterLink, TableModule, ButtonModule, InputTextModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './admin-list.html',
})
export class AdminList {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);

  /** Per-page editors. Only pages listed here expose Add / row-edit. */
  private static readonly EDITORS: Record<string, Editor> = {
    sessions: { createLabel: 'Add Session', createLink: ['/session-create'], editBase: '/sessions-edit' },
  };

  /** entity key from the route (/list/:page) */
  readonly pageKey = input.required<string>({ alias: 'page' });

  protected readonly editor = computed<Editor | null>(() => AdminList.EDITORS[this.pageKey()] ?? null);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<SimpleListResponse | null>(null);
  protected readonly filter = signal('');

  protected readonly rows = computed<SimpleListRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  constructor() {
    effect(() => {
      const p = this.pageKey();
      if (p) this.load(p);
    });
  }

  private load(page: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.filter.set('');
    this.rpc.execute<SimpleListResponse>('SimpleListRequest', { page }).subscribe({
      next: (d) => {
        this.data.set(d);
        this.page.set(d.title || 'Administration');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  reload(): void {
    this.load(this.pageKey());
  }

  /** Navigate to the dedicated editor for a row (only when the page has one). */
  openRow(row: SimpleListRow): void {
    const ed = this.editor();
    if (ed && row.id != null) this.router.navigate([ed.editBase, row.id]);
  }

  exportCsv(): void {
    const d = this.data();
    if (!d) return;
    const cols = d.columns ?? [];
    const rows = this.rows().map((r) => (r.cells ?? []).map((c) => c ?? ''));
    downloadCsv(d.title || 'export', cols, rows);
  }
}
