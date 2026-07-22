import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ExportService } from '../../core/export';
import { ApiError, PageMessage, SolverReportsResponse, TableInterface } from '../../core/models';
import { RpcTable } from '../../shared/rpc-table';

/**
 * Solution Reports (legacy GWT SolutionReportsPage). Calls the command bean
 * SolverReportsRequest (empty body) and renders the SolverReportsResponse:
 * a list of TableInterface reports for the currently loaded course-timetabling
 * solution, each drawn with the shared RpcTable, plus any page messages.
 *
 * Deferred: per-table CSV/PDF export (legacy uses EncodeQueryRpcRequest +
 * the /export servlet) and the preference legend / column descriptions.
 */
@Component({
  selector: 'app-solution-reports',
  imports: [ButtonModule, CardModule, MessageModule, ProgressSpinnerModule, RpcTable],
  templateUrl: './solution-reports.html',
})
export class SolutionReports implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private exportSvc = inject(ExportService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly response = signal<SolverReportsResponse | null>(null);

  protected readonly messages = computed<PageMessage[]>(() => this.response()?.pageMessages ?? []);
  protected readonly tables = computed<TableInterface[]>(() => this.response()?.tables ?? []);

  ngOnInit(): void {
    this.page.set('Solution Reports');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<SolverReportsResponse>('SolverReportsRequest', {}).subscribe({
      next: (res) => {
        this.response.set(res ?? {});
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  severity(m: PageMessage): 'error' | 'warn' | 'info' {
    return m.type === 'ERROR' ? 'error' : m.type === 'WARNING' ? 'warn' : 'info';
  }

  /**
   * Server-side per-table export via the /export servlet (legacy
   * SolutionReportsPage.exportData). sort is not tracked here, so 0 is used.
   */
  exportServer(t: TableInterface, format: 'pdf' | 'csv'): void {
    const query = 'output=solution-reports.' + format + '&sort=0&table=' + (t.tableId ?? '');
    this.exportSvc.export(query);
  }

  rowCount(t: TableInterface): number {
    return t.rows?.length ?? 0;
  }
}
