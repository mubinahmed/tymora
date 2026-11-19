import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, TableInterface, TableRowInterface } from '../../core/models';
import { RpcTable } from '../../shared/rpc-table';
import { ClassAssignmentDialog } from './class-assignment-dialog';

/**
 * Generic solver reporting screen: calls a TableInterface-returning command
 * (name + page title supplied via route `data`, bound through
 * withComponentInputBinding) with an empty filter and renders the result with
 * the shared RpcTable. Drives Assigned Classes, Not-assigned Classes, etc.
 */
@Component({
  selector: 'app-solver-report',
  imports: [ButtonModule, MessageModule, ProgressSpinnerModule, RpcTable, ClassAssignmentDialog],
  templateUrl: './solver-report.html',
})
export class SolverReport {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** bound from route data */
  readonly rpcName = input.required<string>({ alias: 'rpc' });
  readonly title = input.required<string>();
  /** optional request body (route data `req`); defaults to an empty filter */
  readonly req = input<unknown>(undefined);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly table = signal<TableInterface | null>(null);

  /** class reports whose rows are class ids: clicking a row opens the class assignment dialog */
  protected readonly classRows = computed(() =>
    ['NotAssignedClassesRequest', 'AssignedClassesRequest'].includes(this.rpcName()),
  );
  protected readonly dialogVisible = signal(false);
  protected readonly dialogClassId = signal<number | null>(null);

  protected openClass(row: TableRowInterface): void {
    if (row.id == null) return;
    this.dialogClassId.set(row.id);
    this.dialogVisible.set(true);
  }

  constructor() {
    effect(() => {
      const name = this.rpcName();
      this.page.set(this.title());
      if (name) this.load(name);
    });
  }

  load(name = this.rpcName()): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<TableInterface>(name, this.req() ?? { filter: { parameters: [] } }).subscribe({
      next: (t) => {
        this.table.set(t);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
