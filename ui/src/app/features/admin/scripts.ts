import { Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  DeleteScriptRpcRequest,
  ExecuteScriptRpcRequest,
  GetQueueTableRpcRequest,
  QueueItemInterface,
  SaveOrUpdateScriptRpcRequest,
  ScriptInterface,
  ScriptOptionsInterface,
  ScriptParameterInterface,
} from '../../core/models';

/**
 * Scripts admin page (command pattern). Lists custom scripts, runs a selected one
 * with typed parameters, and shows the execution queue with logs. Also supports
 * create/edit/delete of scripts via a dialog.
 *
 * RPCs (org.unitime.timetable.gwt.shared.ScriptInterface.*):
 *   GetScriptOptionsRpcRequest   -> ScriptOptionsInterface (engines, permissions, canAdd, email)
 *   LoadAllScriptsRpcRequest     -> ScriptInterface[]       (GwtRpcResponseList)
 *   ExecuteScriptRpcRequest      -> QueueItemInterface
 *   GetQueueTableRpcRequest      -> QueueItemInterface[]    (also deletes when deleteId set)
 *   SaveOrUpdateScriptRpcRequest -> ScriptInterface
 *   DeleteScriptRpcRequest       -> ScriptInterface
 *
 * Deferred vs legacy GWT: file-upload parameters render as plain text; date/time/
 * datetime/slot parameters render as text inputs (no calendar/time widget); script
 * export (EncodeQuery) is not wired.
 */
@Component({
  selector: 'app-scripts',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MultiSelectModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
    TextareaModule,
    CheckboxModule,
    InputNumberModule,
    DialogModule,
    TagModule,
  ],
  templateUrl: './scripts.html',
})
export class Scripts implements OnInit, OnDestroy {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly options = signal<ScriptOptionsInterface | null>(null);
  protected readonly scripts = signal<ScriptInterface[]>([]);
  protected readonly queue = signal<QueueItemInterface[]>([]);
  protected readonly selectedQueue = signal<QueueItemInterface | null>(null);

  /** Only scripts the user can act on are selectable (mirrors legacy filter). */
  protected readonly visibleScripts = computed(() =>
    this.scripts().filter((s) => s.canExecute || s.canEdit || s.canDelete),
  );

  protected selectedId: number | null = null;
  protected readonly selectedScript = computed(
    () => this.visibleScripts().find((s) => s.id === this.selectedId) ?? null,
  );

  /** Per-parameter working values keyed by parameter name (raw ngModel values). */
  protected paramModel: Record<string, unknown> = {};

  // execution email
  protected sendEmail = false;
  protected emailAddresses = '';
  protected readonly executing = signal(false);

  // edit dialog state
  protected readonly dialogVisible = signal(false);
  protected readonly saving = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected edit: ScriptInterface = {};
  protected editParams: ScriptParameterInterface[] = [];

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.page.set('Scripts');
    this.reload();
    this.refreshQueue();
    this.pollTimer = setInterval(() => this.refreshQueue(), 5000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
  }

  reload(selectId: number | null = this.selectedId): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ScriptOptionsInterface>('GetScriptOptionsRpcRequest', {}).subscribe({
      next: (opts) => {
        this.options.set(opts ?? {});
        if (opts?.email && !this.emailAddresses) this.emailAddresses = opts.email;
        this.rpc.execute<ScriptInterface[]>('LoadAllScriptsRpcRequest', {}).subscribe({
          next: (list) => {
            this.scripts.set(list ?? []);
            const still = this.visibleScripts().some((s) => s.id === selectId);
            this.selectedId = still ? selectId : null;
            this.onScriptChange();
            this.loading.set(false);
          },
          error: (e: ApiError) => this.fail(e),
        });
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onScriptChange(): void {
    this.error.set(null);
    this.notice.set(null);
    this.paramModel = {};
    const s = this.selectedScript();
    if (!s?.parameters) return;
    for (const p of s.parameters) {
      if (!p.name) continue;
      this.paramModel[p.name] = this.defaultFor(p);
    }
  }

  private defaultFor(p: ScriptParameterInterface): unknown {
    const type = (p.type ?? '').toLowerCase();
    const def = p.value ?? p.default ?? null;
    if (this.isMultiSelect(p)) {
      return def ? def.split(',').filter((x) => x) : [];
    }
    if (this.hasOptions(p)) return def ?? null;
    if (type === 'boolean') return def != null ? def.toLowerCase() === 'true' : false;
    if (this.isNumber(p)) return def != null && def !== '' ? Number(def) : null;
    return def ?? '';
  }

  hasOptions(p: ScriptParameterInterface): boolean {
    return !!p.options && p.options.length > 0;
  }
  isMultiSelect(p: ScriptParameterInterface): boolean {
    return this.hasOptions(p) && !!p.multiSelect;
  }
  isSingleSelect(p: ScriptParameterInterface): boolean {
    return this.hasOptions(p) && !p.multiSelect;
  }
  isBoolean(p: ScriptParameterInterface): boolean {
    return (p.type ?? '').toLowerCase() === 'boolean';
  }
  isTextarea(p: ScriptParameterInterface): boolean {
    return (p.type ?? '').toLowerCase() === 'textarea';
  }
  isNumber(p: ScriptParameterInterface): boolean {
    const t = (p.type ?? '').toLowerCase();
    return ['integer', 'int', 'long', 'short', 'byte', 'number', 'float', 'double'].includes(t);
  }
  isDecimal(p: ScriptParameterInterface): boolean {
    const t = (p.type ?? '').toLowerCase();
    return ['number', 'float', 'double'].includes(t);
  }
  isPlainText(p: ScriptParameterInterface): boolean {
    return (
      !this.hasOptions(p) && !this.isBoolean(p) && !this.isTextarea(p) && !this.isNumber(p)
    );
  }

  paramLabel(p: ScriptParameterInterface): string {
    return p.label && p.label.length ? p.label : (p.name ?? '');
  }

  private collectParameters(): { [key: string]: string } {
    const out: { [key: string]: string } = {};
    const s = this.selectedScript();
    for (const p of s?.parameters ?? []) {
      if (!p.name) continue;
      const raw = this.paramModel[p.name];
      if (raw == null || raw === '') continue;
      if (Array.isArray(raw)) {
        if (raw.length) out[p.name] = raw.join(',');
      } else if (typeof raw === 'boolean') {
        out[p.name] = raw ? 'true' : 'false';
      } else {
        out[p.name] = String(raw);
      }
    }
    return out;
  }

  execute(): void {
    const s = this.selectedScript();
    if (!s?.id) return;
    this.executing.set(true);
    this.error.set(null);
    this.notice.set(null);
    const request: ExecuteScriptRpcRequest = {
      id: s.id,
      name: s.name,
      parameters: this.collectParameters(),
      email: this.sendEmail && this.emailAddresses ? this.emailAddresses : undefined,
    };
    this.rpc.execute<QueueItemInterface>('ExecuteScriptRpcRequest', request).subscribe({
      next: (item) => {
        this.executing.set(false);
        this.notice.set(`Started "${s.name}".`);
        this.refreshQueue(item?.id ?? null);
      },
      error: (e: ApiError) => {
        this.executing.set(false);
        this.error.set(e.message);
      },
    });
  }

  refreshQueue(selectId: string | null = null): void {
    const request: GetQueueTableRpcRequest = {};
    this.rpc.execute<QueueItemInterface[]>('GetQueueTableRpcRequest', request).subscribe({
      next: (list) => {
        this.queue.set(list ?? []);
        if (selectId) {
          const found = this.queue().find((q) => q.id === selectId) ?? null;
          if (found) this.selectQueue(found);
        } else {
          const cur = this.selectedQueue();
          if (cur) this.selectedQueue.set(this.queue().find((q) => q.id === cur.id) ?? null);
        }
      },
      error: () => {
        /* transient poll failures are non-fatal */
      },
    });
  }

  deleteQueueItem(item: QueueItemInterface): void {
    if (!item.id) return;
    const request: GetQueueTableRpcRequest = { deleteId: item.id };
    this.rpc.execute<QueueItemInterface[]>('GetQueueTableRpcRequest', request).subscribe({
      next: (list) => {
        this.queue.set(list ?? []);
        if (this.selectedQueue()?.id === item.id) this.selectedQueue.set(null);
      },
      error: (e: ApiError) => this.error.set(e.message),
    });
  }

  selectQueue(item: QueueItemInterface): void {
    this.selectedQueue.set(this.selectedQueue()?.id === item.id ? null : item);
  }

  // ---- create / edit / delete dialog ----

  openAdd(): void {
    this.edit = {};
    this.editParams = [{}];
    this.dialogError.set(null);
    this.dialogVisible.set(true);
  }

  openEdit(): void {
    const s = this.selectedScript();
    if (!s) return;
    this.edit = { ...s };
    this.editParams = [
      ...(s.parameters ?? []).map((p) => ({ ...p })),
      {}, // trailing empty row for adding
    ];
    this.dialogError.set(null);
    this.dialogVisible.set(true);
  }

  get isEdit(): boolean {
    return this.edit.id != null;
  }

  addParamRow(): void {
    this.editParams = [...this.editParams, {}];
  }
  removeParamRow(i: number): void {
    this.editParams = this.editParams.filter((_, idx) => idx !== i);
    if (!this.editParams.length) this.editParams = [{}];
  }

  saveScript(): void {
    const name = (this.edit.name ?? '').trim();
    if (!name) {
      this.dialogError.set('Name is required.');
      return;
    }
    if (!this.edit.engine) {
      this.dialogError.set('Engine is required.');
      return;
    }
    if (!(this.edit.script ?? '').trim()) {
      this.dialogError.set('Script is required.');
      return;
    }
    const dup = this.scripts().some((s) => s.name === name && s.id !== this.edit.id);
    if (dup) {
      this.dialogError.set('A script with this name already exists.');
      return;
    }
    const params = this.editParams.filter((p) => (p.name ?? '').trim());
    const names = new Set<string>();
    for (const p of params) {
      if (names.has(p.name!)) {
        this.dialogError.set(`Duplicate parameter name: ${p.name}`);
        return;
      }
      names.add(p.name!);
      if (!(p.type ?? '').trim()) {
        this.dialogError.set(`Parameter "${p.name}" requires a type.`);
        return;
      }
    }
    const script: ScriptInterface = { ...this.edit, name, parameters: params };
    const request: SaveOrUpdateScriptRpcRequest = { script };
    this.saving.set(true);
    this.dialogError.set(null);
    this.rpc.execute<ScriptInterface>('SaveOrUpdateScriptRpcRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.reload(res?.id ?? null);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.dialogError.set(e.message);
      },
    });
  }

  deleteScript(): void {
    const s = this.selectedScript();
    if (!s?.id) return;
    if (!confirm(`Delete script "${s.name}"?`)) return;
    const request: DeleteScriptRpcRequest = { id: s.id, name: s.name };
    this.rpc.execute<ScriptInterface>('DeleteScriptRpcRequest', request).subscribe({
      next: () => this.reload(null),
      error: (e: ApiError) => this.error.set(e.message),
    });
  }

  statusSeverity(status?: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch ((status ?? '').toLowerCase()) {
      case 'finished':
        return 'success';
      case 'running':
        return 'info';
      case 'queued':
        return 'warn';
      case 'failed':
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
