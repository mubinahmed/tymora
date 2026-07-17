import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ExportService } from '../../core/export';
import {
  ApiError,
  AssignmentHistoryFilterResponse,
  AssignmentHistoryResponse,
  FilterInterface,
  FilterParameterInterface,
  PageMessage,
  PreferenceInterface,
  TableInterface,
} from '../../core/models';
import { RpcTable } from '../../shared/rpc-table';

/**
 * Assignment History (legacy key: assignmentHistory) — command-pattern screen.
 * Init loads the filter definition + preference legend via
 * AssignmentHistoryFilterRequest (returns AssignmentHistoryFilterResponse, which
 * extends FilterInterface with a preferences[] legend). Search posts the current
 * filter values as AssignmentHistoryRequest and renders the returned
 * AssignmentHistoryResponse (a TableInterface of solver assignment changes over
 * time) with the shared RpcTable. Deferred: CSV/PDF export and print, plus the
 * per-row suggestions dialog link (see notes).
 */
@Component({
  selector: 'app-assignment-history',
  imports: [
    FormsModule,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    CardModule,
    ProgressSpinnerModule,
    RpcTable,
  ],
  templateUrl: './assignment-history.html',
})
export class AssignmentHistory implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private exportSvc = inject(ExportService);

  protected readonly loading = signal(false);
  protected readonly searching = signal(false);
  protected readonly searched = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Filter parameters as returned by the backend; values are mutated in place. */
  protected readonly params = signal<FilterParameterInterface[]>([]);
  protected readonly preferences = signal<PreferenceInterface[]>([]);

  protected readonly table = signal<TableInterface | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly pageMessages = signal<PageMessage[]>([]);

  protected readonly hasRows = computed(() => (this.table()?.rows ?? []).length > 0);

  ngOnInit(): void {
    this.page.set('Assignment History');
    this.loadFilter();
  }

  /** Load the filter definition (single "simpleMode" boolean today) + legend. */
  private loadFilter(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<AssignmentHistoryFilterResponse>('AssignmentHistoryFilterRequest', {}).subscribe({
      next: (res) => {
        const params = res.parameters ?? [];
        // Seed each parameter's working value from its default.
        for (const p of params) if (p.value == null) p.value = p.default ?? '';
        this.params.set(params);
        this.preferences.set(res.preferences ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  isBoolean(p: FilterParameterInterface): boolean {
    return (p.type ?? '').toLowerCase() === 'boolean';
  }

  isList(p: FilterParameterInterface): boolean {
    return (p.type ?? '').toLowerCase() === 'list' || (p.options?.length ?? 0) > 0;
  }

  /** Two-way bridge for boolean params (stored as "1"/"0" strings). */
  boolValue(p: FilterParameterInterface): boolean {
    return p.value === '1' || p.value === 'true';
  }
  setBool(p: FilterParameterInterface, v: boolean): void {
    p.value = v ? '1' : '0';
  }

  search(): void {
    this.searching.set(true);
    this.error.set(null);
    const filter: FilterInterface = { parameters: this.params() };
    this.rpc.execute<AssignmentHistoryResponse>('AssignmentHistoryRequest', { filter }).subscribe({
      next: (res) => {
        this.table.set(res);
        this.message.set(res.message ?? null);
        this.pageMessages.set(res.pageMessages ?? []);
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.searching.set(false);
      },
    });
  }

  /**
   * Server-side export via the /export servlet (legacy AssignmentHistoryPage.
   * exportData). The filter is serialized exactly like PageFilter.getQuery():
   * "&<name>=<encodeURIComponent(value)>" for every parameter whose value is
   * non-null. sort is not tracked here, so 0 is used.
   */
  exportServer(format: 'pdf' | 'csv'): void {
    let query = 'output=assignment-history.' + format;
    for (const p of this.params()) {
      if (!p.name) continue;
      const value = p.value;
      if (value != null) query += '&' + p.name + '=' + encodeURIComponent(value);
    }
    query += '&sort=0';
    this.exportSvc.export(query);
  }

  pmSeverity(m: PageMessage): 'error' | 'warn' | 'info' {
    const t = String(m.type ?? '').toUpperCase();
    return t === 'ERROR' ? 'error' : t === 'WARNING' ? 'warn' : 'info';
  }
}
