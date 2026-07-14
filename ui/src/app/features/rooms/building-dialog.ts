import { Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { ApiError, BuildingInterface, UpdateBuildingRequest } from '../../core/models';

/**
 * Create/edit a building via UpdateBuildingRequest (CREATE or UPDATE). The
 * dialog owns its form + submit state and emits (saved) after a successful
 * write so the parent can reload. Delete is handled by the parent from the row.
 */
@Component({
  selector: 'app-building-dialog',
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    CheckboxModule,
    MessageModule,
  ],
  templateUrl: './building-dialog.html',
})
export class BuildingDialog {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private messages = inject(MessageService);

  /** two-way visibility (parent binds [visible]/(visibleChange)) */
  readonly visible = model<boolean>(false);
  /** the building to edit, or null to create */
  readonly building = input<BuildingInterface | null>(null);
  readonly saved = output<void>();

  protected readonly saving = signal(false);
  protected readonly isEdit = computed(() => this.building()?.id != null);

  protected readonly form = this.fb.group({
    abbreviation: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    externalId: [''],
    x: [null as number | null],
    y: [null as number | null],
    updateRoomCoordinates: [false],
  });

  constructor() {
    // Sync the form whenever the target building (or open state) changes.
    effect(() => {
      const b = this.building();
      this.visible(); // re-sync on open
      this.form.reset({
        abbreviation: b?.abbreviation ?? '',
        name: b?.name ?? '',
        externalId: b?.externalId ?? '',
        x: b?.x ?? null,
        y: b?.y ?? null,
        updateRoomCoordinates: false,
      });
    });
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
    const source = this.building();
    const building: BuildingInterface = {
      id: source?.id,
      abbreviation: v.abbreviation ?? '',
      name: v.name ?? '',
      externalId: v.externalId || undefined,
      x: v.x ?? undefined,
      y: v.y ?? undefined,
    };
    const request: UpdateBuildingRequest = {
      action: this.isEdit() ? 'UPDATE' : 'CREATE',
      building,
      updateRoomCoordinates: this.isEdit() ? !!v.updateRoomCoordinates : undefined,
    };

    this.saving.set(true);
    this.rpc.execute<BuildingInterface>('UpdateBuildingRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.isEdit() ? 'Building updated' : 'Building created',
          detail: `${res.abbreviation} — ${res.name}`,
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
