import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/** One loaded solver instance (course / exam / student / instructor). */
interface SolverInstance {
  type?: string;
  owner?: string;
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
}

interface ManageSolversResponse {
  solvers?: SolverInstance[];
  servers?: SolverServerRow[];
}

/**
 * Manage Solvers (legacy manageSolvers.action). Read-only report of the solver
 * instances currently loaded on the in-memory solver server(s) plus the list of
 * available servers, backed by ManageSolversRequest -> ManageSolversBackend.
 *
 * The legacy page's mutating operations (select/unload a solver, shutdown/reset/
 * reconnect/enable/disable a server) are intentionally not migrated - this screen
 * is a status view only.
 */
@Component({
  selector: 'app-manage-solvers',
  imports: [TableModule, CardModule, TagModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './manage-solvers.html',
})
export class ManageSolvers implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly response = signal<ManageSolversResponse | null>(null);

  protected readonly solvers = computed<SolverInstance[]>(() => this.response()?.solvers ?? []);
  protected readonly servers = computed<SolverServerRow[]>(() => this.response()?.servers ?? []);

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

  typeLabel(type?: string): string {
    return (type && this.TYPE_LABEL[type]) || type || '';
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
}
