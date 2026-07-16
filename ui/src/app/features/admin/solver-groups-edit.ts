import { Component, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/** Mirrors SolverGroupEditInterface.SolverGroupInfo (Gson: iField -> field). */
interface SolverGroupInfo {
  uniqueId: number;
  name: string;
  abbv: string;
  departments: string;
  committed: boolean;
  canEdit: boolean;
  canDelete: boolean;
}

/** Mirrors SolverGroupEditInterface.SolverGroupEditResponse. */
interface SolverGroupEditResponse {
  canAdd: boolean;
  groups: SolverGroupInfo[];
}

type Operation = 'LOAD' | 'SAVE' | 'DELETE';

/** Mirrors SolverGroupEditInterface.SolverGroupEditRequest. */
interface SolverGroupEditRequest {
  operation: Operation;
  uniqueId?: number | null;
  name?: string;
  abbv?: string;
}

const RPC = 'SolverGroupEditRequest';

/**
 * Create / edit / delete solver groups for the current academic session
 * (migration of solverGroupEdit.action). Only the name and abbreviation are
 * edited here; department membership, timetable managers and solutions are
 * managed elsewhere and left untouched on save.
 */
@Component({
  selector: 'app-solver-groups-edit',
  imports: [
    ReactiveFormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './solver-groups-edit.html',
})
export class SolverGroupsEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private fb = inject(FormBuilder);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<SolverGroupEditResponse | null>(null);
  protected readonly groups = computed<SolverGroupInfo[]>(() => this.data()?.groups ?? []);
  protected readonly canAdd = computed(() => this.data()?.canAdd ?? false);

  protected readonly dialogVisible = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly isEdit = computed(() => this.editingId() != null);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    abbv: ['', [Validators.required, Validators.maxLength(50)]],
  });

  constructor() {
    this.page.set('Solver Groups');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<SolverGroupEditResponse>(RPC, { operation: 'LOAD' } as SolverGroupEditRequest).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  add(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', abbv: '' });
    this.dialogVisible.set(true);
  }

  edit(g: SolverGroupInfo): void {
    this.editingId.set(g.uniqueId);
    this.form.reset({ name: g.name, abbv: g.abbv });
    this.dialogVisible.set(true);
  }

  cancel(): void {
    this.dialogVisible.set(false);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: SolverGroupEditRequest = {
      operation: 'SAVE',
      uniqueId: this.editingId(),
      name: (v.name ?? '').trim(),
      abbv: (v.abbv ?? '').trim(),
    };
    this.saving.set(true);
    this.rpc.execute<SolverGroupEditResponse>(RPC, request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.isEdit() ? 'Saved' : 'Created',
          detail: request.name,
        });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(g: SolverGroupInfo): void {
    this.confirm.confirm({
      header: 'Delete solver group',
      message: `Delete "${g.name}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(g),
    });
  }

  private doDelete(g: SolverGroupInfo): void {
    const request: SolverGroupEditRequest = { operation: 'DELETE', uniqueId: g.uniqueId };
    this.rpc.execute<SolverGroupEditResponse>(RPC, request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.messages.add({ severity: 'success', summary: 'Solver group deleted', detail: g.name });
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
