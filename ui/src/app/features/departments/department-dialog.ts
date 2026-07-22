import { Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { ApiError, DepartmentInterface2, StatusOption, UpdateDepartmentRequest } from '../../core/models';

/**
 * Create/edit a department via UpdateDepartmentRequest (CREATE/UPDATE). Sets the
 * fields UpdateDepartmentBackend actually reads: code/abbreviation/name,
 * external id, distribution-pref priority, status (statusType = StatusOption
 * reference), external-manager block, and the scheduling/preference flags.
 */
@Component({
  selector: 'app-department-dialog',
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    CheckboxModule,
    SelectModule,
    MessageModule,
  ],
  templateUrl: './department-dialog.html',
})
export class DepartmentDialog {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private messages = inject(MessageService);

  readonly visible = model<boolean>(false);
  readonly item = input<DepartmentInterface2 | null>(null);
  readonly statuses = input<StatusOption[]>([]);
  readonly fundingEnabled = input<boolean>(false);
  readonly saved = output<void>();

  protected readonly saving = signal(false);
  protected readonly isEdit = computed(() => this.item()?.uniqueId != null);
  protected readonly extManager = signal(false);

  protected readonly form = this.fb.group({
    deptCode: ['', [Validators.required, Validators.maxLength(50)]],
    abbreviation: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    externalId: [''],
    distributionPrefPriority: [0 as number | null],
    statusType: [null as string | null],
    externalManager: [false],
    externalMgrAbbv: [''],
    externalMgrLabel: [''],
    allowEvents: [false],
    allowStudentScheduling: [false],
    allowReqTime: [false],
    allowReqRoom: [false],
    allowReqDistribution: [false],
    inheritInstructorPreferences: [false],
    externalFundingDept: [false],
  });

  constructor() {
    effect(() => {
      const d = this.item();
      this.visible();
      this.form.reset({
        deptCode: d?.deptCode ?? '',
        abbreviation: d?.abbreviation ?? '',
        name: d?.name ?? '',
        externalId: d?.externalId ?? '',
        distributionPrefPriority: d?.distributionPrefPriority ?? 0,
        statusType: d?.statusType ?? null,
        externalManager: d?.externalManager ?? false,
        externalMgrAbbv: d?.externalMgrAbbv ?? '',
        externalMgrLabel: d?.externalMgrLabel ?? '',
        allowEvents: d?.allowEvents ?? false,
        allowStudentScheduling: d?.allowStudentScheduling ?? false,
        allowReqTime: d?.allowReqTime ?? false,
        allowReqRoom: d?.allowReqRoom ?? false,
        allowReqDistribution: d?.allowReqDistribution ?? false,
        inheritInstructorPreferences: d?.inheritInstructorPreferences ?? false,
        externalFundingDept: d?.externalFundingDept ?? false,
      });
      this.extManager.set(d?.externalManager ?? false);
    });
  }

  onExtManagerChange(on: boolean): void {
    this.extManager.set(on);
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
    const department: DepartmentInterface2 = {
      uniqueId: source?.uniqueId,
      deptCode: v.deptCode ?? '',
      abbreviation: v.abbreviation ?? '',
      name: v.name ?? '',
      externalId: v.externalId || undefined,
      distributionPrefPriority: v.distributionPrefPriority ?? 0,
      statusType: v.statusType ?? undefined,
      externalManager: !!v.externalManager,
      externalMgrAbbv: v.externalManager ? v.externalMgrAbbv || undefined : undefined,
      externalMgrLabel: v.externalManager ? v.externalMgrLabel || undefined : undefined,
      allowEvents: !!v.allowEvents,
      allowStudentScheduling: !!v.allowStudentScheduling,
      allowReqTime: !!v.allowReqTime,
      allowReqRoom: !!v.allowReqRoom,
      allowReqDistribution: !!v.allowReqDistribution,
      inheritInstructorPreferences: !!v.inheritInstructorPreferences,
      externalFundingDept: !!v.externalFundingDept,
    };
    const request: UpdateDepartmentRequest = { action: this.isEdit() ? 'UPDATE' : 'CREATE', department };

    this.saving.set(true);
    this.rpc.execute<DepartmentInterface2>('UpdateDepartmentRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.isEdit() ? 'Department updated' : 'Department created',
          detail: `${res.deptCode} — ${res.name}`,
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
