import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  AttributeInterface,
  AttributeTypeInterface,
  GetInstructorAttributesRequest,
  InstructorAttributePropertiesInterface,
  InstructorInterface_DepartmentInterface,
  UpdateInstructorAttributeRequest,
} from '../../core/models';
import { AttributeDialog } from './attribute-dialog';

/**
 * Instructor Attributes (command pattern). The list backend requires a
 * departmentId (permission-checked), so this screen picks a department first —
 * like the offering search — then lists/edits that department's attributes.
 */
@Component({
  selector: 'app-instructor-attributes',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    AttributeDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './instructor-attributes.html',
})
export class InstructorAttributes implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly departments = signal<InstructorInterface_DepartmentInterface[]>([]);
  protected readonly types = signal<AttributeTypeInterface[]>([]);
  protected readonly attributes = signal<AttributeInterface[]>([]);

  protected departmentId: number | null = null;

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<AttributeInterface | null>(null);

  ngOnInit(): void {
    this.page.set('Instructor Attributes');
    this.rpc.execute<InstructorAttributePropertiesInterface>('InstructorAttributePropertiesRequest', {}).subscribe({
      next: (p) => {
        this.departments.set(p.departments ?? []);
        this.types.set(p.attributeTypes ?? []);
        this.departmentId = p.lastDepartmentId ?? p.departments?.[0]?.id ?? null;
        this.loading.set(false);
        if (this.departmentId != null) this.loadAttributes();
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  loadAttributes(): void {
    if (this.departmentId == null) return;
    this.loadingList.set(true);
    this.error.set(null);
    const request: GetInstructorAttributesRequest = { departmentId: this.departmentId };
    this.rpc.execute<AttributeInterface[]>('GetInstructorAttributesRequest', request).subscribe({
      next: (list) => {
        this.attributes.set(list ?? []);
        this.loadingList.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingList.set(false);
      },
    });
  }

  openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  openEdit(a: AttributeInterface): void {
    this.editing.set({ ...a });
    this.dialogVisible.set(true);
  }

  onSaved(): void {
    this.loadAttributes();
  }

  confirmDelete(a: AttributeInterface): void {
    this.confirm.confirm({
      header: 'Delete attribute',
      message: `Delete "${a.code} — ${a.name}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(a),
    });
  }

  private doDelete(a: AttributeInterface): void {
    const request: UpdateInstructorAttributeRequest = { deleteAttributeId: a.id };
    this.rpc.execute<AttributeInterface>('UpdateInstructorAttributeRequest', request).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Attribute deleted', detail: a.code });
        this.loadAttributes();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
