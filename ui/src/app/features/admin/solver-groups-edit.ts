import { Component, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MultiSelectModule } from 'primeng/multiselect';
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
  departmentIds: number[];
  departmentsEditable: boolean;
  committed: boolean;
  canEdit: boolean;
  canDelete: boolean;
}

/** Mirrors SolverGroupEditInterface.DepartmentInfo. */
interface DepartmentInfo {
  uniqueId: number;
  label: string;
  solverGroupId?: number | null;
}

/** Mirrors SolverGroupEditInterface.SolverGroupEditResponse. */
interface SolverGroupEditResponse {
  canAdd: boolean;
  groups: SolverGroupInfo[];
  departments: DepartmentInfo[];
}

type Operation = 'LOAD' | 'SAVE' | 'DELETE';

/** Mirrors SolverGroupEditInterface.SolverGroupEditRequest. */
interface SolverGroupEditRequest {
  operation: Operation;
  uniqueId?: number | null;
  name?: string;
  abbv?: string;
  departmentIds?: number[];
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
    MultiSelectModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './solver-groups-edit.html',
  styles: [
    `
      .form-grid { display: flex; flex-direction: column; gap: 1rem; }
      .field { display: flex; flex-direction: column; gap: 0.35rem; }
      .field label { font-size: 0.8rem; font-weight: 600; }
      .field ::ng-deep p-multiselect { width: 100%; }
      .err { color: var(--p-red-500, #ef4444); font-size: 0.8rem; }
      .hint { color: var(--p-text-muted-color, #6b7280); font-size: 0.8rem; }
    `,
  ],
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
  protected readonly departmentsEditable = signal(true);

  /** All departments in the session that can belong to a solver group. */
  protected readonly allDepartments = computed<DepartmentInfo[]>(() => this.data()?.departments ?? []);

  /**
   * Departments selectable for the group being edited: those with no solver group
   * plus the ones already belonging to this group (a department belongs to at most
   * one solver group per session).
   */
  protected readonly availableDepartments = computed<DepartmentInfo[]>(() => {
    const id = this.editingId();
    return this.allDepartments().filter((d) => d.solverGroupId == null || d.solverGroupId === id);
  });

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    abbv: ['', [Validators.required, Validators.maxLength(50)]],
    departmentIds: [[] as number[]],
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
    this.departmentsEditable.set(true);
    this.form.reset({ name: '', abbv: '', departmentIds: [] });
    this.form.controls.departmentIds.enable();
    this.dialogVisible.set(true);
  }

  edit(g: SolverGroupInfo): void {
    this.editingId.set(g.uniqueId);
    this.departmentsEditable.set(g.departmentsEditable);
    this.form.reset({ name: g.name, abbv: g.abbv, departmentIds: [...(g.departmentIds ?? [])] });
    if (g.departmentsEditable) this.form.controls.departmentIds.enable();
    else this.form.controls.departmentIds.disable();
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
      departmentIds: v.departmentIds ?? [],
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
