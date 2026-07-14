import { Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { PickListModule } from 'primeng/picklist';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import {
  ApiError,
  AttributeInterface,
  AttributeTypeInterface,
  GetInstructorsRequest,
  InstructorInterface2,
  UpdateInstructorAttributeRequest,
} from '../../core/models';

/**
 * Create/edit an instructor attribute via UpdateInstructorAttributeRequest
 * (create/update via `attribute`; no action enum). Sets code/name/type, keeps
 * the attribute in the selected department, and manages instructor membership
 * with a PickList of the department's instructors (diffed to addInstructors /
 * dropInstructors on save). Parent-attribute selection is deferred.
 */
@Component({
  selector: 'app-attribute-dialog',
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    PickListModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './attribute-dialog.html',
})
export class AttributeDialog {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private messages = inject(MessageService);

  readonly visible = model<boolean>(false);
  readonly item = input<AttributeInterface | null>(null);
  readonly types = input<AttributeTypeInterface[]>([]);
  readonly departmentId = input<number | null>(null);
  readonly saved = output<void>();

  protected readonly saving = signal(false);
  protected readonly isEdit = computed(() => this.item()?.id != null);

  // instructor membership
  protected readonly members = signal<InstructorInterface2[]>([]);
  protected readonly available = signal<InstructorInterface2[]>([]);
  protected readonly loadingInstructors = signal(false);
  protected readonly instructorsError = signal<string | null>(null);
  private allInstructors: InstructorInterface2[] = [];
  private loadedDeptId: number | null = null;
  private originalMemberIds = new Set<number>();

  protected readonly form = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    typeId: [null as number | null, Validators.required],
  });

  constructor() {
    effect(() => {
      const a = this.item();
      const open = this.visible();
      this.form.reset({ code: a?.code ?? '', name: a?.name ?? '', typeId: a?.type?.id ?? null });
      const members = (a?.instructors ?? []).slice();
      this.originalMemberIds = new Set(members.map((m) => m.id).filter((x): x is number => x != null));
      this.members.set(members);
      this.recomputeAvailable();
      if (open) this.ensureInstructorsLoaded();
    });
  }

  private ensureInstructorsLoaded(): void {
    const deptId = this.departmentId();
    if (deptId == null) return;
    if (this.loadedDeptId === deptId || this.loadingInstructors()) return;
    this.loadingInstructors.set(true);
    this.instructorsError.set(null);
    const request: GetInstructorsRequest = { departmentId: deptId };
    this.rpc.execute<InstructorInterface2[]>('GetInstructorsRequest', request).subscribe({
      next: (list) => {
        this.allInstructors = list ?? [];
        this.loadedDeptId = deptId;
        this.loadingInstructors.set(false);
        this.recomputeAvailable();
      },
      error: (e: ApiError) => {
        this.loadingInstructors.set(false);
        this.instructorsError.set(e.message);
      },
    });
  }

  private recomputeAvailable(): void {
    const memberIds = new Set(this.members().map((m) => m.id));
    this.available.set(this.allInstructors.filter((i) => !memberIds.has(i.id)));
  }

  instructorName(i: InstructorInterface2): string {
    return i.formattedName || [i.firstName, i.lastName].filter(Boolean).join(' ') || i.externalId || '?';
  }

  cancel(): void {
    this.visible.set(false);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const source = this.item();
    const attribute: AttributeInterface = {
      id: source?.id,
      code: v.code ?? '',
      name: v.name ?? '',
      type: v.typeId != null ? { id: v.typeId } : undefined,
      department: source?.department ?? (this.departmentId() != null ? { id: this.departmentId()! } : undefined),
    };

    const memberIds = this.members()
      .map((m) => m.id)
      .filter((x): x is number => x != null);
    const memberIdSet = new Set(memberIds);
    const addInstructors = memberIds.filter((id) => !this.originalMemberIds.has(id));
    const dropInstructors = [...this.originalMemberIds].filter((id) => !memberIdSet.has(id));

    const request: UpdateInstructorAttributeRequest = { attribute, addInstructors, dropInstructors };

    this.saving.set(true);
    this.rpc.execute<AttributeInterface>('UpdateInstructorAttributeRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.isEdit() ? 'Attribute updated' : 'Attribute created',
          detail: `${res.code} — ${res.name}`,
        });
        this.visible.set(false);
        this.saved.emit();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
