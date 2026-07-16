import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, SimpleListResponse, SimpleListRow } from '../../core/models';

/**
 * Generic read-only listing for legacy Struts admin pages served by the new
 * SimpleListBackend command bean. Routed as /list/:page — the :page key selects
 * the entity list (sessions, managers, solverGroups, …). Columns and rows come
 * from the backend; edit/create is intentionally not offered here (those legacy
 * pages remain the place to mutate until dedicated editors are ported).
 */
@Component({
  selector: 'app-admin-list',
  imports: [TableModule, ButtonModule, InputTextModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './admin-list.html',
})
export class AdminList {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** entity key from the route (/list/:page) */
  readonly pageKey = input.required<string>({ alias: 'page' });

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
}
