import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  InfoPair,
  ListSolutionsRequest,
  ListSolutionsResponse,
  SolutionInfo,
  SolutionOperation,
  TableRowInterface,
} from '../../core/models';

interface Col {
  index: number;
  name: string;
  align: string;
}

/**
 * Saved Timetables (legacy "listSolutions"). Command-pattern screen backed by
 * ListSolutionsRequest → ListSolutionsBackend. INIT loads the current solver
 * state plus the list of saved course-timetabling solutions for the session.
 *
 * Every user action (select/deselect a solution, save/reload/unload the loaded
 * solver, commit/uncommit/update-note/delete a selected solution, load a
 * selected solution or an empty solution into the interactive solver) is the
 * same request with a different SolutionOperation; the backend returns the full
 * refreshed response each time. Button availability is driven by the response
 * `operations` bitmask (SolutionOperation.flag = 1 << ordinal), keyed by
 * solution id ("-1" = the currently loaded solution). CSV export opens the
 * classic /export endpoint directly, mirroring the GWT page.
 */
@Component({
  selector: 'app-list-solutions',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SelectModule,
    TextareaModule,
    MessageModule,
    CardModule,
    TagModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './list-solutions.html',
})
export class ListSolutions implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** SolutionOperation ordinal → used to decode the operations bitmask. */
  private readonly OP_ORDINAL: Record<SolutionOperation, number> = {
    INIT: 0,
    CHECK: 1,
    SELECT: 2,
    DESELECT: 3,
    LOAD: 4,
    LOAD_EMPTY: 5,
    UNLOAD: 6,
    COMMIT: 7,
    UNCOMMIT: 8,
    EXPORT: 9,
    UPDATE_NOTE: 10,
    DELETE: 11,
    RELOAD: 12,
    SAVE: 13,
    SAVE_AS_NEW: 14,
    SAVE_COMMIT: 15,
    SAVE_AS_NEW_COMMIT: 16,
  };

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly response = signal<ListSolutionsResponse | null>(null);

  /** Note text buffers keyed by solution id ("-1" = current loaded solution). */
  protected notes: Record<string, string> = {};

  /** Interactive-solver load parameters (bound to the load panels). */
  protected configurationId: number | null = null;
  protected host: string | null = null;
  protected ownerId: number | null = null;

  protected readonly currentSolution = computed<SolutionInfo | null>(() => this.response()?.currentSolution ?? null);
  protected readonly selectedSolutions = computed<SolutionInfo[]>(() => this.response()?.selectedSolutions ?? []);
  protected readonly rows = computed<TableRowInterface[]>(() => this.response()?.rows ?? []);
  protected readonly hosts = computed<string[]>(() => this.response()?.hosts ?? []);
  protected readonly configurations = computed(() => this.response()?.configurations ?? []);
  protected readonly owners = computed(() => this.response()?.solverOwners ?? []);

  protected readonly cols = computed<Col[]>(() =>
    (this.response()?.header ?? [])
      .map((h, index) => ({ h, index }))
      .filter((c) => c.h.visible !== false)
      .map((c) => ({ index: c.index, name: c.h.name ?? '', align: this.align(c.h.alignment) })),
  );

  /** Whether the "load selected into interactive solver" panel applies. */
  protected readonly canLoadSelected = computed(() => {
    const sel = this.selectedSolutions();
    return (
      sel.length > 0 &&
      this.configurations().length > 0 &&
      sel.every((s) => this.canExecute(s.id ?? -1, 'LOAD'))
    );
  });

  /** Whether the "load empty solution" panel applies. */
  protected readonly showLoadEmpty = computed(
    () =>
      !this.canLoadSelected() &&
      this.configurations().length > 0 &&
      this.canExecute(-1, 'LOAD_EMPTY') &&
      this.owners().length > 0,
  );

  ngOnInit(): void {
    this.page.set('Saved Timetables');
    this.execute('INIT');
  }

  private align(a?: string): string {
    const s = (a ?? '').toUpperCase();
    return s.includes('RIGHT') ? 'right' : s.includes('CENTER') ? 'center' : 'left';
  }

  /** Decode the response operations bitmask for a given solution id. */
  canExecute(id: number, op: SolutionOperation): boolean {
    const ops = this.response()?.operations?.[String(id)];
    if (ops == null) return false;
    return (ops & (1 << this.OP_ORDINAL[op])) !== 0;
  }

  isRowSelected(row: TableRowInterface): boolean {
    return row.selected === true;
  }

  onRowClick(row: TableRowInterface): void {
    if (row.id == null) return;
    this.execute(row.selected ? 'DESELECT' : 'SELECT', { solutionId: row.id });
  }

  /** Note buffer accessor (used by two-way binding in the template). */
  noteKey(id: number | undefined): string {
    return id == null ? '-1' : String(id);
  }

  /**
   * Run a solution operation. Confirmed (destructive) operations prompt first;
   * EXPORT is handled entirely on the client by opening the classic endpoint.
   */
  execute(operation: SolutionOperation, opts: { solutionId?: number } = {}): void {
    if (operation === 'EXPORT') {
      const url = 'export?output=solution.csv&type=course' + (opts.solutionId != null ? '&solution=' + opts.solutionId : '');
      window.open(url, '_blank');
      return;
    }

    const confirmMsg = this.confirmationFor(operation);
    if (confirmMsg && !window.confirm(confirmMsg)) return;

    const request: ListSolutionsRequest = { operation };
    switch (operation) {
      case 'SAVE':
      case 'SAVE_AS_NEW':
      case 'SAVE_COMMIT':
      case 'SAVE_AS_NEW_COMMIT':
      case 'RELOAD':
        request.note = this.notes['-1'];
        break;
      case 'UPDATE_NOTE':
      case 'COMMIT':
      case 'UNCOMMIT':
        if (opts.solutionId != null) {
          request.solutionIds = [opts.solutionId];
          request.note = this.notes[String(opts.solutionId)];
        }
        break;
      case 'DELETE':
      case 'SELECT':
      case 'DESELECT':
        if (opts.solutionId != null) request.solutionIds = [opts.solutionId];
        break;
      case 'LOAD':
        request.configurationId = this.configurationId ?? undefined;
        request.host = this.host ?? undefined;
        break;
      case 'LOAD_EMPTY':
        request.configurationId = this.configurationId ?? undefined;
        request.host = this.host ?? undefined;
        request.ownerId = this.ownerId ?? undefined;
        break;
    }

    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ListSolutionsResponse>('ListSolutionsRequest', request).subscribe({
      next: (res) => this.populate(res),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private populate(res: ListSolutionsResponse): void {
    this.response.set(res);
    // Seed note buffers from the returned solution data.
    this.notes = {};
    if (res.currentSolution) this.notes['-1'] = res.currentSolution.note ?? '';
    for (const s of res.selectedSolutions ?? []) this.notes[this.noteKey(s.id)] = s.note ?? '';
    // Seed interactive-solver defaults.
    this.configurationId = res.configurationId ?? this.configurations()[0]?.id ?? null;
    this.host = res.host ?? this.hosts()[0] ?? null;
    this.ownerId = this.owners()[0]?.id ?? null;
    // Surface backend-reported errors (e.g. commit failures) without losing the refreshed view.
    if (res.errors?.length) this.error.set(res.errors.filter(Boolean).join('\n'));
    this.loading.set(false);
  }

  private confirmationFor(op: SolutionOperation): string | null {
    switch (op) {
      case 'UNLOAD':
        return 'Do you really want to unload the current solution?';
      case 'SAVE':
        return 'Do you really want to save the solution (all previously saved data will be overwritten)?';
      case 'SAVE_AS_NEW':
        return 'Do you really want to save the solution as a new one?';
      case 'SAVE_COMMIT':
        return 'Do you really want to save and commit the solution?';
      case 'SAVE_AS_NEW_COMMIT':
        return 'Do you really want to save the solution as a new one and commit it?';
      case 'COMMIT':
        return 'Do you really want to commit the selected solution?';
      case 'UNCOMMIT':
        return 'Do you really want to uncommit the selected solution?';
      case 'DELETE':
        return 'Do you really want to delete the selected solution?';
      default:
        return null;
    }
  }

  pairs(s: SolutionInfo): InfoPair[] {
    return s.pairs ?? [];
  }
}
