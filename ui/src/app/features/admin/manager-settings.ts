import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Manager Settings (legacy Struts managerSettings page) — personal settings for
 * the signed-in user. Backed by the ManagerSettingsRequest command bean: LOAD
 * returns every defined Settings key with the current user's value + the allowed
 * value/label choices; SAVE persists ONE value against the signed-in user only
 * (UserContext.setProperty). Each row is edited inline via a dropdown and saved
 * on demand — no global reference data is ever mutated.
 */

// --- request / response DTOs (inline; match Gson field naming iField -> field) ---
type Operation = 'LOAD' | 'SAVE';

interface SettingOption {
  value?: string;
  label?: string;
}

interface SettingRecord {
  id?: number;
  key?: string;
  name?: string;
  value?: string;
  defaultValue?: string;
  options?: SettingOption[];
}

interface ManagerSettingsRequest {
  operation: Operation;
  key?: string;
  value?: string;
}

interface ManagerSettingsResponse {
  editable?: boolean;
  records?: SettingRecord[];
}

/** A row's working copy: the currently-selected value, separate from the persisted one. */
interface Row extends SettingRecord {
  selected: string;
  dirty: boolean;
  savingRow: boolean;
}

@Component({
  selector: 'app-manager-settings',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './manager-settings.html',
})
export class ManagerSettings {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<Row[]>([]);
  protected readonly editable = signal(false);

  constructor() {
    this.page.set('Manager Settings');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.send({ operation: 'LOAD' }, () => this.loading.set(false), (e) => {
      this.error.set(e.message);
      this.loading.set(false);
    });
  }

  private toRows(records: SettingRecord[]): Row[] {
    return records.map((r) => ({
      ...r,
      selected: r.value ?? r.defaultValue ?? '',
      dirty: false,
      savingRow: false,
    }));
  }

  private send(request: ManagerSettingsRequest, done: () => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<ManagerSettingsResponse>('ManagerSettingsRequest', request).subscribe({
      next: (res) => {
        this.rows.set(this.toRows(res.records ?? []));
        this.editable.set(!!res.editable);
        done();
      },
      error: (e: ApiError) => fail(e),
    });
  }

  labelFor(r: Row): string {
    const opt = (r.options ?? []).find((o) => o.value === r.value);
    return opt?.label ?? r.value ?? '';
  }

  onChange(r: Row): void {
    r.dirty = r.selected !== (r.value ?? r.defaultValue ?? '');
  }

  save(r: Row): void {
    if (!r.key) return;
    r.savingRow = true;
    this.rpc
      .execute<ManagerSettingsResponse>('ManagerSettingsRequest', {
        operation: 'SAVE',
        key: r.key,
        value: r.selected,
      } satisfies ManagerSettingsRequest)
      .subscribe({
        next: (res) => {
          this.rows.set(this.toRows(res.records ?? []));
          this.editable.set(!!res.editable);
          this.messages.add({ severity: 'success', summary: 'Saved', detail: r.name ?? r.key });
        },
        error: (e: ApiError) => {
          r.savingRow = false;
          r.dirty = true;
          this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
        },
      });
  }
}
