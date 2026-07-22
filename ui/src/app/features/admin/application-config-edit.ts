import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';

const RPC = 'ApplicationConfigEditRequest';

interface ConfigItem {
  key?: string;
  value?: string;
  description?: string;
}
interface EditRequest {
  operation: 'LOAD' | 'SAVE';
  key?: string;
  value?: string;
  description?: string;
}
interface EditResponse {
  editable?: boolean;
  items?: ConfigItem[];
}

/**
 * Application Configuration editor (Angular migration of applicationConfig.action).
 * Lists all persisted ApplicationConfig entries; when the user has
 * ApplicationConfigEdit, the value/description of an existing key can be changed
 * via the ApplicationConfigEditRequest command bean (SAVE). Creating/deleting
 * keys and per-session overrides are not offered here. Editing application-wide
 * configuration is powerful — changes persist immediately and may require a
 * configuration refresh / restart to take effect.
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
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './application-config-edit.html',
})
export class ApplicationConfigEdit implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editable = signal(false);
  protected readonly items = signal<ConfigItem[]>([]);
  protected readonly filter = signal('');

  protected readonly dialogVisible = signal(false);
  protected readonly edit = signal<{ key: string; value: string; description: string } | null>(null);

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
    this.edit.set({ key: item.key ?? '', value: item.value ?? '', description: item.description ?? '' });
    this.dialogVisible.set(true);
  }

  save(): void {
    const e = this.edit();
    if (!e) return;
    this.saving.set(true);
    const req: EditRequest = { operation: 'SAVE', key: e.key, value: e.value, description: e.description };
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

  setValue(value: string): void {
    const e = this.edit();
    if (e) this.edit.set({ ...e, value });
  }
  setDescription(description: string): void {
    const e = this.edit();
    if (e) this.edit.set({ ...e, description });
  }

  exportCsv(): void {
    const rows = this.rows().map((c) => [c.key ?? '', c.value ?? '', c.description ?? '']);
    downloadCsv('application-config', ['Key', 'Value', 'Description'], rows);
  }
}
