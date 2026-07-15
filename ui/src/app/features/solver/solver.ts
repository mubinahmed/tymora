import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressBarModule } from 'primeng/progressbar';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, SolverOperation, SolverPageRequest, SolverPageResponse, SolverType } from '../../core/models';

/**
 * Solver dashboard (command pattern) — the first screen to drive the async facade
 * trio. Long operations (START / RELOAD) go through rpc.executeAsync (submit →
 * poll /api/rpc/async → result); quick ones (INIT status read, STOP) are sync.
 * While the solver reports `working`, status is auto-polled for live progress.
 *
 * Deferred: configuration parameter editing and solution save/commit/publish.
 */
@Component({
  selector: 'app-solver',
  imports: [
    FormsModule,
    CardModule,
    ButtonModule,
    SelectModule,
    MultiSelectModule,
    TagModule,
    MessageModule,
    ProgressBarModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './solver.html',
})
export class Solver implements OnInit, OnDestroy {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly solverTypes = [
    { label: 'Course Timetabling', value: 'COURSE' as SolverType },
    { label: 'Examination', value: 'EXAM' as SolverType },
    { label: 'Student Scheduling', value: 'STUDENT' as SolverType },
    { label: 'Instructor Scheduling', value: 'INSTRUCTOR' as SolverType },
  ];
  protected type: SolverType = 'COURSE';

  protected readonly loading = signal(true);
  protected readonly busy = signal(false); // an async op (start/reload) is in flight
  protected readonly error = signal<string | null>(null);
  protected readonly resp = signal<SolverPageResponse | null>(null);

  protected ownerIds: number[] = [];
  protected configurationId: number | null = null;

  protected readonly working = computed(() => !!this.resp()?.working);
  protected readonly logTail = computed(() => (this.resp()?.log ?? []).slice(-40));
  /** solution save/commit/publish controls are meaningful once a solution exists */
  protected readonly hasSolution = computed(() => !!this.resp()?.currentSolution || !!this.resp()?.bestSolution);

  private pollHandle: ReturnType<typeof setTimeout> | null = null;
  private destroyed = false;

  ngOnInit(): void {
    this.page.set('Solver');
    this.init();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.pollHandle) clearTimeout(this.pollHandle);
  }

  onTypeChange(): void {
    this.ownerIds = [];
    this.configurationId = null;
    this.init();
  }

  /** Read current solver state (quick). */
  private init(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send('INIT').subscribe({
      next: (r) => {
        this.apply(r);
        // adopt server's current selection defaults
        this.ownerIds = r.ownerIds ?? [];
        this.configurationId = r.configurationId ?? r.configurations?.[0]?.id ?? null;
        this.loading.set(false);
        this.schedulePoll();
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  refresh(): void {
    this.send('INIT').subscribe({ next: (r) => { this.apply(r); this.schedulePoll(); }, error: () => {} });
  }

  /** Long-running: submit + poll the async facade, then reflect the result. */
  start(): void {
    this.runAsync('START', 'Start');
  }
  reload(): void {
    this.runAsync('RELOAD', 'Reload');
  }

  stop(): void {
    this.busy.set(true);
    this.send('STOP').subscribe({
      next: (r) => { this.apply(r); this.busy.set(false); this.schedulePoll(); },
      error: (e: ApiError) => { this.error.set(e.message); this.busy.set(false); },
    });
  }

  /**
   * Solution operations (save / save-as-new / commit / restore-best / unload).
   * Consequential ones confirm first; all run through the async facade.
   */
  operation(op: SolverOperation, label: string, confirmText?: string): void {
    if (confirmText) {
      this.confirm.confirm({
        header: label,
        message: confirmText,
        icon: 'pi pi-exclamation-triangle',
        accept: () => this.runAsync(op, label),
      });
    } else {
      this.runAsync(op, label);
    }
  }

  /**
   * Solver operations are SYNCHRONOUS: they return quickly (the solve itself runs
   * on the server's own solver threads); the client then polls INIT while the
   * server reports `working`. (Running these through the async facade fails —
   * SolverPageBackend uses request-scoped state that isn't available off-thread,
   * confirmed live — so they must not go through /api/rpc/async.)
   */
  private runAsync(op: SolverOperation, label?: string): void {
    this.busy.set(true);
    this.error.set(null);
    const request: SolverPageRequest = {
      type: this.type,
      operation: op,
      ownerIds: this.ownerIds,
      configurationId: this.configurationId ?? undefined,
    };
    this.rpc.execute<SolverPageResponse>('SolverPageRequest', request).subscribe({
      next: (r) => {
        this.apply(r);
        this.busy.set(false);
        this.schedulePoll();
        if (label) this.messages.add({ severity: 'success', summary: label, detail: 'Completed' });
      },
      error: (e: ApiError) => { this.error.set(e.message ?? 'Operation failed'); this.busy.set(false); },
    });
  }

  private send(op: SolverOperation) {
    const request: SolverPageRequest = { type: this.type, operation: op };
    return this.rpc.execute<SolverPageResponse>('SolverPageRequest', request);
  }

  private apply(r: SolverPageResponse): void {
    this.resp.set(r);
  }

  /** Keep polling status while the solver is working. */
  private schedulePoll(): void {
    if (this.pollHandle) clearTimeout(this.pollHandle);
    if (this.destroyed || !this.working()) return;
    this.pollHandle = setTimeout(() => {
      this.send('INIT').subscribe({ next: (r) => { this.apply(r); this.schedulePoll(); }, error: () => {} });
    }, 2500);
  }
}
