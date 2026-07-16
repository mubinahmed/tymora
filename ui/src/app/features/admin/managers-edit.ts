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

/** Mirrors TimetableManagerEditInterface (Gson: Java iField -> field). */
type Operation = 'LIST' | 'LOAD' | 'SAVE' | 'DELETE';

interface TimetableManagerEditRequest {
  operation: Operation;
  uniqueId?: number;
  externalUniqueId?: string;
  firstName?: string;
  middleName?: string;
  lastName?: string;
  academicTitle?: string;
  emailAddress?: string;
}

interface ManagerLine {
  uniqueId: number;
  externalUniqueId?: string;
  name?: string;
  email?: string;
  canEdit: boolean;
  canDelete: boolean;
}

interface TimetableManagerEditResponse {
  uniqueId?: number;
  externalUniqueId?: string;
  firstName?: string;
  middleName?: string;
  lastName?: string;
  academicTitle?: string;
  emailAddress?: string;
  managers?: ManagerLine[];
  canAdd?: boolean;
}

const RPC = 'TimetableManagerEditRequest';

/**
 * Create/Edit Timetabling Managers (legacy timetableManagerList.action). Lists
 * managers and edits their core identity fields via a dialog. Roles, department,
 * settings and solver-group assignments are DEFERRED — not editable here.
 */
@Component({
  selector: 'app-managers-edit',
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
  templateUrl: './managers-edit.html',
})
export class ManagersEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private fb = inject(FormBuilder);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly managers = signal<ManagerLine[]>([]);
  protected readonly canAdd = signal(false);

  protected readonly dialogVisible = signal(false);
  protected readonly saving = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly heading = computed(() => (this.editingId() == null ? 'New Manager' : 'Edit Manager'));

  protected readonly form = this.fb.group({
    externalUniqueId: ['', [Validators.maxLength(40)]],
    firstName: ['', [Validators.maxLength(100)]],
    middleName: ['', [Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    academicTitle: ['', [Validators.maxLength(50)]],
    emailAddress: ['', [Validators.maxLength(200)]],
  });

  constructor() {
    this.page.set('Timetabling Managers');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<TimetableManagerEditResponse>(RPC, { operation: 'LIST' } as TimetableManagerEditRequest).subscribe({
      next: (res) => {
        this.managers.set(res.managers ?? []);
        this.canAdd.set(!!res.canAdd);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form.reset({
      externalUniqueId: '',
      firstName: '',
      middleName: '',
      lastName: '',
      academicTitle: '',
      emailAddress: '',
    });
    this.dialogVisible.set(true);
  }

  openEdit(m: ManagerLine): void {
    this.editingId.set(m.uniqueId);
    this.dialogVisible.set(true);
    // Load the full editable record (the list row only carries display fields).
    this.rpc
      .execute<TimetableManagerEditResponse>(RPC, { operation: 'LOAD', uniqueId: m.uniqueId } as TimetableManagerEditRequest)
      .subscribe({
        next: (res) => {
          this.form.reset({
            externalUniqueId: res.externalUniqueId ?? '',
            firstName: res.firstName ?? '',
            middleName: res.middleName ?? '',
            lastName: res.lastName ?? '',
            academicTitle: res.academicTitle ?? '',
            emailAddress: res.emailAddress ?? '',
          });
        },
        error: (e: ApiError) => {
          this.dialogVisible.set(false);
          this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message });
        },
      });
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
    const request: TimetableManagerEditRequest = {
      operation: 'SAVE',
      uniqueId: this.editingId() ?? undefined,
      externalUniqueId: v.externalUniqueId?.trim() || undefined,
      firstName: v.firstName?.trim() || undefined,
      middleName: v.middleName?.trim() || undefined,
      lastName: v.lastName?.trim() || undefined,
      academicTitle: v.academicTitle?.trim() || undefined,
      emailAddress: v.emailAddress?.trim() || undefined,
    };
    this.saving.set(true);
    this.rpc.execute<TimetableManagerEditResponse>(RPC, request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.editingId() == null ? 'Manager created' : 'Manager saved',
        });
        this.reload();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(m: ManagerLine): void {
    this.confirm.confirm({
      header: 'Delete manager',
      message: `Delete "${m.name ?? m.externalUniqueId ?? m.uniqueId}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(m),
    });
  }

  private doDelete(m: ManagerLine): void {
    this.rpc
      .execute<TimetableManagerEditResponse>(RPC, { operation: 'DELETE', uniqueId: m.uniqueId } as TimetableManagerEditRequest)
      .subscribe({
        next: () => {
          this.messages.add({ severity: 'success', summary: 'Manager deleted', detail: m.name });
          this.reload();
        },
        error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
      });
  }
}
