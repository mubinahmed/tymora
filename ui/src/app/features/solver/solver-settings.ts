import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  ParamInfo,
  SettingInfo,
  SolverSettingEditResponse,
  SolverSettingListResponse,
  SolverSettingUpdateRequest,
} from './solver-settings.models';

/**
 * Solver Configurations (legacy solverSettings.action) — list + edit + delete of
 * SolverPredefinedSettings, backed by SolverSettingList/Edit/Update/DeleteRequest. Editing
 * a config sets its name/description and its parameter overrides (grouped, typed, with
 * use-default). Add-new and export stay on the legacy screen.
 */
@Component({
  selector: 'app-solver-settings',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    CheckboxModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './solver-settings.html',
})
export class SolverSettings implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly settings = signal<SettingInfo[]>([]);
  protected readonly editData = signal<SolverSettingEditResponse | null>(null);

  ngOnInit(): void {
    this.page.set('Solver Configurations');
    this.loadList();
  }

  private loadList(): void {
    this.loading.set(true);
    this.rpc.execute<SolverSettingListResponse>('SolverSettingListRequest', {}).subscribe({
      next: (d) => {
        this.settings.set(d.settings ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  edit(s: SettingInfo): void {
    this.loading.set(true);
    this.rpc.execute<SolverSettingEditResponse>('SolverSettingEditRequest', { settingId: s.id }).subscribe({
      next: (d) => {
        this.editData.set(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message });
        this.loading.set(false);
      },
    });
  }

  backToList(): void {
    this.editData.set(null);
    this.loadList();
  }

  /** boolean params are edited as checkboxes over the "true"/"false" string value. */
  boolValue(p: ParamInfo): boolean {
    return p.value === 'true';
  }
  setBool(p: ParamInfo, on: boolean): void {
    p.value = on ? 'true' : 'false';
  }

  save(): void {
    const d = this.editData();
    if (!d) return;
    this.saving.set(true);
    const params = (d.groups ?? [])
      .flatMap((g) => g.params ?? [])
      .map((p) => ({ defId: p.defId, value: p.value, useDefault: !!p.useDefault }));
    const request: SolverSettingUpdateRequest = {
      settingId: d.id,
      name: d.name ?? '',
      description: d.description ?? '',
      params,
    };
    this.rpc.execute<SolverSettingEditResponse>('SolverSettingUpdateRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.editData.set(res);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Solver configuration updated.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  askDelete(s: SettingInfo): void {
    this.confirm.confirm({
      header: 'Delete solver configuration',
      message: `Delete the solver configuration "${s.name}"?`,
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.doDelete(s),
    });
  }

  private doDelete(s: SettingInfo): void {
    this.rpc.execute<SolverSettingListResponse>('SolverSettingDeleteRequest', { settingId: s.id }).subscribe({
      next: (d) => {
        this.settings.set(d.settings ?? []);
        this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Solver configuration deleted.' });
      },
      error: (e: ApiError) => {
        this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
      },
    });
  }
}
