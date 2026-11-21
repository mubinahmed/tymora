import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DialogModule } from 'primeng/dialog';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';

const RPC = 'ApplicationConfigEditRequest';

interface ConfigItem {
  key?: string;
  value?: string;
  description?: string;
  /** true = global ApplicationConfig, false = SessionConfig of the current academic session */
  allSessions?: boolean;
}
interface EditRequest {
  operation: 'LOAD' | 'SAVE' | 'CREATE' | 'DELETE';
  key?: string;
  value?: string;
  description?: string;
  allSessions?: boolean;
}
interface EditState {
  key: string;
  value: string;
  description: string;
  allSessions: boolean;
  isNew: boolean;
}
interface EditResponse {
  editable?: boolean;
  items?: ConfigItem[];
}

/**
 * Application Configuration editor (Angular migration of applicationConfig.action).
 * Lists the global ApplicationConfig entries plus the SessionConfig entries of the
 * current academic session. With ApplicationConfigEdit the user can change a value
 * (SAVE), add a new setting (CREATE — legacy "Add Setting", global or current
 * session only) and delete a setting (DELETE). The backend pushes every change to
 * the running application (SolverServerService.setApplicationProperty) like the
 * legacy page, so no restart is needed.
 */
@Component({
  selector: 'app-application-config-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    DialogModule,
    CheckboxModule,
    TagModule,
    ConfirmDialogModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './application-config-edit.html',
})
export class ApplicationConfigEdit implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editable = signal(false);
  protected readonly items = signal<ConfigItem[]>([]);
  protected readonly filter = signal('');

  protected readonly dialogVisible = signal(false);
  protected readonly edit = signal<EditState | null>(null);

  /** Validation message for the key of a new setting (null = valid). */
  protected readonly keyError = computed<string | null>(() => {
    const e = this.edit();
    if (!e || !e.isNew) return null;
    const key = e.key.trim();
    if (!key) return 'Key is required.';
    if (/\s/.test(key)) return 'Key must not contain spaces.';
    const lower = key.toLowerCase();
    const clash = this.items().find((c) => (c.key ?? '').toLowerCase() === lower && (c.allSessions !== false) === e.allSessions);
    if (clash) return `Setting "${clash.key}" already exists${e.allSessions ? '' : ' for the current session'}.`;
    return null;
  });

  protected readonly rows = computed<ConfigItem[]>(() => {
    const f = this.filter().trim().toLowerCase();
    const all = this.items();
    if (!f) return all;
    return all.filter((c) => `${c.key ?? ''} ${c.value ?? ''} ${c.description ?? ''}`.toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Application Configuration');
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<EditResponse>(RPC, { operation: 'LOAD' } as EditRequest).subscribe({
      next: (res) => {
        this.editable.set(!!res.editable);
        this.items.set(res.items ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  openEdit(item: ConfigItem): void {
    if (!this.editable()) return;
    this.edit.set({
      key: item.key ?? '',
      value: item.value ?? '',
      description: item.description ?? '',
      allSessions: item.allSessions !== false,
      isNew: false,
    });
    this.dialogVisible.set(true);
  }

  openAdd(): void {
    if (!this.editable()) return;
    this.edit.set({ key: '', value: '', description: '', allSessions: true, isNew: true });
    this.dialogVisible.set(true);
  }

  save(): void {
    const e = this.edit();
    if (!e) return;
    if (e.isNew && this.keyError()) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: this.keyError()! });
      return;
    }
    this.saving.set(true);
    const req: EditRequest = {
      operation: e.isNew ? 'CREATE' : 'SAVE',
      key: e.key.trim(),
      value: e.value,
      description: e.description,
      allSessions: e.allSessions,
    };
    this.rpc.execute<EditResponse>(RPC, req).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.items.set(res.items ?? []);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: e.key });
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: err.message });
      },
    });
  }

  confirmDelete(item: ConfigItem): void {
    if (!this.editable()) return;
    const scope = item.allSessions === false ? ' (current session)' : '';
    this.confirm.confirm({
      header: 'Delete setting',
      message: `Delete setting "${item.key}"${scope}? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        const req: EditRequest = { operation: 'DELETE', key: item.key, allSessions: item.allSessions !== false };
        this.rpc.execute<EditResponse>(RPC, req).subscribe({
          next: (res) => {
            this.items.set(res.items ?? []);
            this.messages.add({ severity: 'success', summary: 'Deleted', detail: item.key });
          },
          error: (err: ApiError) =>
            this.messages.add({ severity: 'error', summary: 'Delete failed', detail: err.message }),
        });
      },
    });
  }

  setKey(key: string): void {
    const e = this.edit();
    if (e) this.edit.set({ ...e, key });
  }
  setAllSessions(allSessions: boolean): void {
    const e = this.edit();
    if (e) this.edit.set({ ...e, allSessions });
  }
  setValue(value: string): void {
    const e = this.edit();
    if (e) this.edit.set({ ...e, value });
  }
  setDescription(description: string): void {
    const e = this.edit();
    if (e) this.edit.set({ ...e, description });
  }

  exportCsv(): void {
    const rows = this.rows().map((c) => [
      c.key ?? '',
      c.value ?? '',
      c.description ?? '',
      c.allSessions === false ? 'Current session' : 'All sessions',
    ]);
    downloadCsv('application-config', ['Key', 'Value', 'Description', 'Applies To'], rows);
  }
}
