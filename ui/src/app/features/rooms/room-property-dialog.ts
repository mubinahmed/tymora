import { Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { PickListModule } from 'primeng/picklist';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import {
  ApiError,
  Entity,
  FeatureInterface,
  FeatureTypeInterface,
  FilterRpcResponse,
  GroupInterface,
  RoomFilterRpcRequest,
  RoomInterface_AcademicSessionInterface,
  RoomInterface_DepartmentInterface,
  UpdateRoomFeatureRequest,
  UpdateRoomGroupRequest,
} from '../../core/models';

export type RoomPropertyKind = 'group' | 'feature';
type RoomProperty = GroupInterface | FeatureInterface;

/**
 * Shared create/edit dialog for Room Groups and Room Features (both extend
 * RoomPropertyInterface). Edits core fields plus **room membership**: a PickList
 * of the session's rooms (loaded via RoomFilterRpcRequest) whose target is the
 * current members (item.rooms). On save it diffs members against the original
 * set into addLocations / dropLocations for the update request.
 */
@Component({
  selector: 'app-room-property-dialog',
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    CheckboxModule,
    SelectModule,
    MultiSelectModule,
    MessageModule,
    PickListModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './room-property-dialog.html',
})
export class RoomPropertyDialog {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private messages = inject(MessageService);

  readonly visible = model<boolean>(false);
  readonly kind = input.required<RoomPropertyKind>();
  readonly item = input<RoomProperty | null>(null);
  readonly featureTypes = input<FeatureTypeInterface[]>([]);
  readonly departments = input<RoomInterface_DepartmentInterface[]>([]);
  readonly futureSessions = input<RoomInterface_AcademicSessionInterface[]>([]);
  readonly saved = output<void>();

  protected readonly saving = signal(false);
  protected readonly isEdit = computed(() => this.item()?.id != null);
  protected readonly isGroup = computed(() => this.kind() === 'group');
  protected readonly heading = computed(
    () => `${this.isEdit() ? 'Edit' : 'New'} ${this.isGroup() ? 'Room Group' : 'Room Feature'}`,
  );

  // room membership
  protected readonly members = signal<Entity[]>([]);
  protected readonly available = signal<Entity[]>([]);
  protected readonly loadingRooms = signal(false);
  protected readonly roomsError = signal<string | null>(null);
  private allRooms: Entity[] = [];
  private roomsLoaded = false;
  private originalMemberIds = new Set<number>();

  /** Mirrors the department control so the template can react (null => Global). */
  protected readonly scopeDeptId = signal<number | null>(null);

  protected readonly form = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
    abbv: ['', [Validators.required, Validators.maxLength(20)]],
    description: [''],
    default: [false],
    type: [null as number | null], // feature type id (features only)
    department: [null as number | null], // null => Global; otherwise departmental
    futureSessions: [[] as number[]], // apply to these future academic sessions too
  });

  onScopeChange(deptId: number | null): void {
    this.scopeDeptId.set(deptId);
  }

  constructor() {
    effect(() => {
      const it = this.item();
      const open = this.visible();
      const deptId = it?.department?.id ?? null;
      this.form.reset({
        label: it?.label ?? '',
        abbv: it?.abbv ?? '',
        description: it?.description ?? '',
        default: (it as GroupInterface | null)?.default ?? false,
        type: (it as FeatureInterface | null)?.type?.id ?? null,
        department: deptId,
        futureSessions: [],
      });
      this.scopeDeptId.set(deptId);
      const members = (it?.rooms ?? []).slice();
      this.originalMemberIds = new Set(members.map((m) => m.uniqueId).filter((x): x is number => x != null));
      this.members.set(members);
      this.recomputeAvailable();
      if (open) this.ensureRoomsLoaded();
    });
  }

  private ensureRoomsLoaded(): void {
    if (this.roomsLoaded || this.loadingRooms()) return;
    this.loadingRooms.set(true);
    this.roomsError.set(null);
    const request: RoomFilterRpcRequest = { command: 'ENUMERATE', options: {} };
    this.rpc.execute<FilterRpcResponse>('RoomFilterRpcRequest', request).subscribe({
      next: (res) => {
        this.allRooms = res.entities?.['results'] ?? [];
        this.roomsLoaded = true;
        this.loadingRooms.set(false);
        this.recomputeAvailable();
      },
      error: (e: ApiError) => {
        this.loadingRooms.set(false);
        this.roomsError.set(e.message);
      },
    });
  }

  /** available = all session rooms minus current members (by uniqueId). */
  private recomputeAvailable(): void {
    const memberIds = new Set(this.members().map((m) => m.uniqueId));
    this.available.set(this.allRooms.filter((r) => !memberIds.has(r.uniqueId)));
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
    const department = v.department != null ? this.departments().find((d) => d.id === v.department) : undefined;
    const base = {
      id: source?.id,
      abbv: v.abbv ?? '',
      label: v.label ?? '',
      description: v.description || undefined,
      department, // undefined => Global
    };
    const futureSessions = v.futureSessions ?? [];

    // Diff membership (PickList mutates the bound arrays in place).
    const memberIds = this.members()
      .map((m) => m.uniqueId)
      .filter((x): x is number => x != null);
    const memberIdSet = new Set(memberIds);
    const addLocations = memberIds.filter((id) => !this.originalMemberIds.has(id));
    const dropLocations = [...this.originalMemberIds].filter((id) => !memberIdSet.has(id));

    const [requestName, request] = this.isGroup()
      ? [
          'UpdateRoomGroupRequest',
          {
            // `default` only applies to global groups.
            group: { ...base, default: !department && !!v.default },
            addLocations,
            dropLocations,
            futureSessions,
          } as UpdateRoomGroupRequest,
        ]
      : [
          'UpdateRoomFeatureRequest',
          {
            feature: { ...base, type: v.type != null ? { id: v.type } : undefined },
            addLocations,
            dropLocations,
            futureSessions,
          } as UpdateRoomFeatureRequest,
        ];

    this.saving.set(true);
    this.rpc.execute<RoomProperty>(requestName as string, request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.isEdit() ? 'Saved' : 'Created',
          detail: `${res.abbv} — ${res.label}`,
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
