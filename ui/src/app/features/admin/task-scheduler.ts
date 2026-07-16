import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  ContactInterface,
  DeleteTaskDetailsRpcRequest,
  ExecutionStatus,
  GetTaskExecutionLogRpcRequest,
  TaskExecutionInterface,
  TaskExecutionLogInterface,
  TaskInterface,
  TaskOptionsInterface,
} from '../../core/models';

/**
 * Task Scheduler admin page (legacy GWT TasksPage, command pattern). Lists the
 * scheduled (periodic) tasks for the current academic session, with a read-only
 * detail view that shows the task's script, owner, parameters and its schedule of
 * executions. Supports deleting a task and viewing an execution's log.
 *
 * RPCs (org.unitime.timetable.gwt.shared.TaskInterface.*):
 *   GetTaskOptionsRpcRequest       -> TaskOptionsInterface        (canAdd, manager, session name, scripts)
 *   GetTasksRpcRequest             -> TaskInterface[]             (GwtRpcResponseList)
 *   DeleteTaskDetailsRpcRequest    -> TaskInterface               ({ taskId })
 *   GetTaskExecutionLogRpcRequest  -> TaskExecutionLogInterface   ({ taskExecutionId })
 *
 * Deferred vs legacy: creating/editing a task (the TaskEditor schedule builder over
 * SaveTaskDetailsRpcRequest, which needs the script-parameter + calendar/slot widgets)
 * is not implemented; output-file download (TaskOutputFileServlet) is shown as a name.
 */
@Component({
  selector: 'app-task-scheduler',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
    TagModule,
    DialogModule,
  ],
  templateUrl: './task-scheduler.html',
})
export class TaskScheduler implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly options = signal<TaskOptionsInterface | null>(null);
  protected readonly tasks = signal<TaskInterface[]>([]);
  protected readonly selected = signal<TaskInterface | null>(null);

  protected readonly sessionName = computed(() => this.options()?.session?.name ?? '');

  // execution-log dialog
  protected readonly logVisible = signal(false);
  protected readonly logLoading = signal(false);
  protected readonly logText = signal<string>('');

  private readonly dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  ngOnInit(): void {
    this.page.set('Task Scheduler');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<TaskOptionsInterface>('GetTaskOptionsRpcRequest', {}).subscribe({
      next: (opts) => {
        this.options.set(opts ?? {});
        this.rpc.execute<TaskInterface[]>('GetTasksRpcRequest', {}).subscribe({
          next: (list) => {
            this.tasks.set(list ?? []);
            // keep the open detail in sync if it still exists
            const cur = this.selected();
            if (cur) this.selected.set(this.tasks().find((t) => t.id === cur.id) ?? null);
            this.loading.set(false);
          },
          error: (e: ApiError) => this.fail(e),
        });
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  openDetail(task: TaskInterface): void {
    if (!task.canView) return;
    this.selected.set(task);
    this.error.set(null);
  }

  back(): void {
    this.selected.set(null);
  }

  deleteTask(task: TaskInterface): void {
    if (!task.id || !task.canEdit) return;
    if (!confirm(`Delete task "${task.name}"?`)) return;
    const request: DeleteTaskDetailsRpcRequest = { taskId: task.id };
    this.loading.set(true);
    this.rpc.execute<TaskInterface>('DeleteTaskDetailsRpcRequest', request).subscribe({
      next: () => {
        this.selected.set(null);
        this.reload();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  viewLog(exec: TaskExecutionInterface): void {
    if (!exec.id) return;
    this.logVisible.set(true);
    this.logLoading.set(true);
    this.logText.set('');
    const request: GetTaskExecutionLogRpcRequest = { taskExecutionId: exec.id };
    this.rpc.execute<TaskExecutionLogInterface>('GetTaskExecutionLogRpcRequest', request).subscribe({
      next: (res) => {
        this.logText.set(res?.log ?? '(no log)');
        this.logLoading.set(false);
      },
      error: (e: ApiError) => {
        this.logText.set(e.message);
        this.logLoading.set(false);
      },
    });
  }

  // ---- display helpers ----

  contactName(c?: ContactInterface): string {
    return c?.formattedName || [c?.firstName, c?.lastName].filter(Boolean).join(' ') || '';
  }

  paramEntries(task: TaskInterface | null): { key: string; value: string }[] {
    const p = task?.parameters ?? {};
    return Object.keys(p).map((key) => ({ key, value: p[key] }));
  }

  executions(task: TaskInterface | null): TaskExecutionInterface[] {
    return [...(task?.executions ?? [])].sort((a, b) => {
      const ay = a.dayOfYear ?? 0;
      const by = b.dayOfYear ?? 0;
      if (ay !== by) return ay - by;
      return (a.slot ?? 0) - (b.slot ?? 0);
    });
  }

  /** Convert a 5-minute slot index to a HH:mm time-of-day string. */
  slotTime(slot?: number): string {
    if (slot == null) return '';
    const min = 5 * slot;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }

  dayName(dow?: number): string {
    if (dow == null || dow < 0 || dow > 6) return '';
    return this.dayNames[dow];
  }

  statusSeverity(status?: ExecutionStatus): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (status) {
      case 'FINISHED':
        return 'success';
      case 'RUNNING':
        return 'info';
      case 'QUEUED':
      case 'CREATED':
        return 'warn';
      case 'FAILED':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.loading.set(false);
  }
}
