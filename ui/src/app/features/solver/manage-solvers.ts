import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/** One loaded solver instance (course / exam / student / instructor / online). */
interface SolverInstance {
  type?: string;
  owner?: string;
  ownerId?: string;
  onlineId?: number;
  session?: string;
  host?: string;
  configuration?: string;
  status?: string;
  created?: string;
  lastUsed?: string;
  progress?: number;
  progressMax?: number;
  working?: boolean;
  passivated?: boolean;
}

/** One available solver server. */
interface SolverServerRow {
  host?: string;
  version?: string;
  started?: string;
  availableMemory?: number;
  cores?: number;
  usage?: number;
  activeInstances?: number;
  workingInstances?: number;
  passivatedInstances?: number;
  active?: boolean;
  local?: boolean;
  coordinator?: boolean;
  available?: boolean;
}

interface ManageSolversResponse {
  solvers?: SolverInstance[];
  servers?: SolverServerRow[];
  navigate?: string;
  clusterEnabled?: boolean;
}

/** Mutating command (ManageSolversOpRequest -> ManageSolversOpBackend). */
interface ManageSolversOpRequest {
  operation: string;
  host?: string | null;
  owner?: string | null;
  type?: string | null;
  onlineId?: number | null;
}

const OP_RPC = 'ManageSolversOpRequest';

/**
 * Manage Solvers (legacy manageSolvers.action). Lists the solver instances currently
 * loaded on the in-memory solver server(s) plus the available servers, and exposes the
 * mutating operations of the legacy page: select / unload a solver instance, reload /
 * unload an online sectioning instance, and shutdown / reset / reconnect / hibernate /
 * enable / disable a server, plus a top-level deselect.
 *
 * Reads are served by ManageSolversRequest -> ManageSolversBackend; mutations by
 * ManageSolversOpRequest -> ManageSolversOpBackend, which returns a refreshed listing.
 */
@Component({
  selector: 'app-manage-solvers',
  imports: [
    TableModule,
    CardModule,
    TagModule,
    MessageModule,
    ButtonModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './manage-solvers.html',
})
export class ManageSolvers implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);
  private router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly response = signal<ManageSolversResponse | null>(null);

  protected readonly solvers = computed<SolverInstance[]>(() => this.response()?.solvers ?? []);
  protected readonly servers = computed<SolverServerRow[]>(() => this.response()?.servers ?? []);
  /** Reconnect server op is only applicable when the solver cluster is enabled (matches legacy). */
  protected readonly clusterEnabled = computed<boolean>(() => this.response()?.clusterEnabled ?? false);

  private readonly TYPE_LABEL: Record<string, string> = {
    COURSE: 'Course Timetabling',
    EXAM: 'Examination',
    STUDENT: 'Student Scheduling',
    INSTRUCTOR: 'Instructor Scheduling',
  };

  ngOnInit(): void {
    this.page.set('Manage Solvers');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ManageSolversResponse>('ManageSolversRequest', {}).subscribe({
      next: (res) => {
        this.response.set(res);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  typeLabel(row: SolverInstance): string {
    if (row.onlineId != null) return 'Online Student Scheduling';
    return (row.type && this.TYPE_LABEL[row.type]) || row.type || '';
  }

  progressLabel(row: SolverInstance): string {
    if (!row.progressMax) return '';
    const pct = Math.round((100 * (row.progress ?? 0)) / row.progressMax);
    return `${pct}%`;
  }

  memory(bytes?: number): string {
    if (bytes == null || bytes < 0) return '';
    const mb = bytes / (1024 * 1024);
    if (mb >= 1024) return `${(mb / 1024).toFixed(1)} GB`;
    return `${Math.round(mb)} MB`;
  }

  /** True for a regular (owner+type) solver instance that can be selected / unloaded. */
  canSelect(row: SolverInstance): boolean {
    return row.onlineId == null && !!row.ownerId && !!row.type;
  }

  // ---------------------------------------------------------------------------
  // Operations
  // ---------------------------------------------------------------------------

  private run(request: ManageSolversOpRequest, successSummary: string): void {
    this.busy.set(true);
    this.rpc.execute<ManageSolversResponse>(OP_RPC, request).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.response.set(res);
        this.messages.add({ severity: 'success', summary: successSummary });
      },
      error: (e: ApiError) => {
        this.busy.set(false);
        this.messages.add({ severity: 'error', summary: 'Operation failed', detail: e.message });
      },
    });
  }

  select(row: SolverInstance): void {
    const type = row.type ?? '';
    this.busy.set(true);
    this.rpc
      .execute<ManageSolversResponse>(OP_RPC, {
        operation: 'Select',
        owner: row.ownerId,
        type,
      } as ManageSolversOpRequest)
      .subscribe({
        next: (res) => {
          this.busy.set(false);
          const target = res.navigate ? res.navigate.replace(/^solver\?type=/, '') : type.toLowerCase();
          this.router.navigate(['/solver'], { queryParams: { type: target } });
        },
        error: (e: ApiError) => {
          this.busy.set(false);
          this.messages.add({ severity: 'error', summary: 'Select failed', detail: e.message });
        },
      });
  }

  unloadSolver(row: SolverInstance): void {
    this.confirm.confirm({
      header: 'Unload solver',
      message: `Unload the ${this.typeLabel(row)} solver${row.owner ? ` of ${row.owner}` : ''}?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () =>
        this.run(
          { operation: 'Unload', owner: row.ownerId, type: row.type },
          'Solver unloaded',
        ),
    });
  }

  reloadOnline(row: SolverInstance): void {
    this.confirm.confirm({
      header: 'Reload online solver',
      message: 'Reload this online student scheduling solver?',
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.run({ operation: 'Reload', onlineId: row.onlineId }, 'Online solver reloaded'),
    });
  }

  unloadOnline(row: SolverInstance): void {
    this.confirm.confirm({
      header: 'Shutdown online solver',
      message: 'Unload this online student scheduling solver?',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () =>
        this.run(
          { operation: 'Unload', onlineId: row.onlineId, host: row.host },
          'Online solver unloaded',
        ),
    });
  }

  deselect(): void {
    this.run({ operation: 'Deselect' }, 'Selection cleared');
  }

  // --- Server operations -----------------------------------------------------

  private serverOp(server: SolverServerRow, operation: string, summary: string, question: string): void {
    this.confirm.confirm({
      header: operation,
      message: question,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.run({ operation, host: server.host }, summary),
    });
  }

  shutdown(server: SolverServerRow): void {
    this.serverOp(server, 'Shutdown', 'Server shut down', `Shut down server ${server.host}?`);
  }

  reset(server: SolverServerRow): void {
    this.serverOp(server, 'Reset', 'Server reset', `Reset server ${server.host}?`);
  }

  reconnect(server: SolverServerRow): void {
    this.serverOp(server, 'Reconnect', 'Server reconnected', `Reconnect server ${server.host}?`);
  }

  hibernate(server: SolverServerRow): void {
    this.serverOp(
      server,
      'Hibernate',
      'Hibernate connection reset',
      `Reconnect the hibernate session of server ${server.host}?`,
    );
  }

  enable(server: SolverServerRow): void {
    // Non-destructive: no confirmation needed.
    this.run({ operation: 'Enable', host: server.host }, 'Server enabled');
  }

  disable(server: SolverServerRow): void {
    this.serverOp(server, 'Disable', 'Server disabled', `Disable server ${server.host}?`);
  }
}
