import { Component, OnInit, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  DepartmentInterface2,
  DepartmentPropertiesInterface,
  DepartmentPropertiesRequest,
  DepartmentsDataResponse,
  GetDepartmentsRequest,
  StatusOption,
  UpdateDepartmentRequest,
} from '../../core/models';
import { DepartmentDialog } from './department-dialog';

@Component({
  selector: 'app-departments',
  imports: [
    TableModule,
    ButtonModule,
    InputTextModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    DepartmentDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './departments.html',
})
export class Departments implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly departments = signal<DepartmentInterface2[]>([]);
  protected readonly canAdd = signal(false);
  protected readonly fundingEnabled = signal(false);
  protected readonly statuses = signal<StatusOption[]>([]);

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<DepartmentInterface2 | null>(null);

  ngOnInit(): void {
    this.page.set('Departments');
    this.loadProperties();
    this.reload();
  }

  private loadProperties(): void {
    const request: DepartmentPropertiesRequest = {};
    this.rpc.execute<DepartmentPropertiesInterface>('DepartmentPropertiesRequest', request).subscribe({
      next: (p) => this.statuses.set(p.statuses ?? []),
      error: () => this.statuses.set([]),
    });
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: GetDepartmentsRequest = {};
    this.rpc.execute<DepartmentsDataResponse>('GetDepartmentsRequest', request).subscribe({
      next: (res) => {
        this.departments.set(res.departments ?? []);
        this.canAdd.set(!!res.canAdd);
        this.fundingEnabled.set(!!res.fundingDeptEnabled);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  openEdit(d: DepartmentInterface2): void {
    this.editing.set({ ...d });
    this.dialogVisible.set(true);
  }

  onSaved(): void {
    this.reload();
  }

  confirmDelete(d: DepartmentInterface2): void {
    this.confirm.confirm({
      header: 'Delete department',
      message: `Delete "${d.deptCode} — ${d.name}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(d),
    });
  }

  private doDelete(d: DepartmentInterface2): void {
    const request: UpdateDepartmentRequest = { action: 'DELETE', department: { uniqueId: d.uniqueId } };
    this.rpc.execute<DepartmentInterface2>('UpdateDepartmentRequest', request).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Department deleted', detail: d.deptCode });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
