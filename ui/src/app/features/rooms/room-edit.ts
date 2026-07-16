import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ReactiveFormsModule, FormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { RoomsService } from './rooms.service';
import { RoomSharingMatrix } from './room-sharing-matrix';
import { PeriodPreferencesGrid } from './period-preferences-grid';
import {
  ApiError,
  AttachmentTypeInterface,
  EventServiceProviderInterface,
  ExamTypeInterface,
  FeatureInterface,
  GetRoomsOfABuildingRequest,
  GroupInterface,
  PeriodPreferenceModel,
  RoomDetailInterface,
  RoomInterface_DepartmentInterface,
  RoomPictureInterface,
  RoomPropertiesInterface,
  RoomPropertiesRequest,
  RoomSharingModel,
} from '../../core/models';

/** GwtConstants.eventStatusName() — index maps to RoomDetailInterface.eventStatus. */
const EVENT_STATUS_NAMES = [
  'No Event Management',
  'Authenticated Users Can Request Events Managers Can Approve',
  'Departmental Users Can Request Events Managers Can Approve',
  'Event Managers Can Request Or Approve Events',
  'Authenticated Users Can Request Events No Approval',
  'Departmental Users Can Request Events No Approval',
  'Event Managers Can Request Events No Approval',
  'Authenticated Users Can Request Events Automatically Approved',
  'Departmental Users Can Request Events Automatically Approved',
  'Event Managers Can Request Events Automatically Approved',
];

/** Apply-to-future-session field groups (label + FutureOperation flag bit). */
const FUTURE_OPS: { bit: number; label: string; defaultNew: boolean; defaultEdit: boolean }[] = [
  { bit: 1, label: 'Room Properties', defaultNew: true, defaultEdit: true },
  { bit: 2, label: 'Exam Properties', defaultNew: true, defaultEdit: true },
  { bit: 4, label: 'Event Properties', defaultNew: true, defaultEdit: true },
  { bit: 8, label: 'Groups', defaultNew: true, defaultEdit: true },
  { bit: 16, label: 'Features', defaultNew: true, defaultEdit: true },
  { bit: 32, label: 'Room Sharing', defaultNew: true, defaultEdit: false },
  { bit: 64, label: 'Period Preferences', defaultNew: true, defaultEdit: false },
  { bit: 128, label: 'Event Availability', defaultNew: true, defaultEdit: false },
  { bit: 256, label: 'Pictures', defaultNew: true, defaultEdit: true },
];

interface Option {
  label: string;
  value: number;
}

interface FutureRow {
  /** futureFlags map key: -sessionId for a new room, the future-room id on edit. */
  key: number;
  label: string;
  session: string;
  selected: boolean;
  ops: Record<number, boolean>;
}

/**
 * Add / Edit Room — routed editor reached from the Rooms list (/rooms/new,
 * /rooms/:id). Full port of the legacy GWT RoomEdit page:
 *   - room properties (type, building, partition, name, coordinates, area,
 *     distance/room check)
 *   - exam properties (examination rooms + exam capacity)
 *   - event properties (department, status, note, email, break time, services)
 *   - room group / feature assignments
 *   - room sharing matrix + event availability matrix (RoomSharingMatrix)
 *   - examination period preferences per exam type (PeriodPreferencesGrid)
 *   - room pictures (retype / delete of existing pictures)
 *   - "apply also to" future sessions / future rooms
 * The whole loaded RoomDetailInterface is merged on save; the per-session
 * futureFlags mask restricts the backend to the field groups actually rendered.
 */
@Component({
  selector: 'app-room-edit',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    CardModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    CheckboxModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    RoomSharingMatrix,
    PeriodPreferencesGrid,
  ],
  providers: [ConfirmationService],
  templateUrl: './room-edit.html',
})
export class RoomEdit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private rooms = inject(RoomsService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private router = inject(Router);

  /** Room id from the route; absent (or 'new') means Add Room. */
  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly props = signal<RoomPropertiesInterface | null>(null);
  protected readonly parentOptions = signal<Option[]>([]);

  /** The full loaded room (edit) or a fresh instance (add); merged on save. */
  private loaded: RoomDetailInterface | null = null;
  /** Reactive mirror of the loaded room, for the read-only display fallbacks. */
  protected readonly view = signal<RoomDetailInterface | null>(null);

  /** Membership by id — dynamic checkbox grids. */
  protected groupSel: Record<number, boolean> = {};
  protected featureSel: Record<number, boolean> = {};
  /** A signal (not a plain map) because activePeriodModels() derives from it. */
  protected readonly examSel = signal<Record<number, boolean>>({});
  protected serviceSel: Record<number, boolean> = {};

  // Phase 2/3/4 sub-models, loaded on demand.
  protected readonly sharingModel = signal<RoomSharingModel | null>(null);
  protected readonly eventAvailModel = signal<RoomSharingModel | null>(null);
  protected readonly periodModels = signal<Record<number, PeriodPreferenceModel>>({});
  protected readonly pictures = signal<RoomPictureInterface[]>([]);
  protected readonly futureRows = signal<FutureRow[]>([]);
  /** Mirrors the event-department select so visibility computeds stay reactive. */
  private readonly eventDeptId = signal<number | null>(null);
  /** Marks a sub-model as dirtied so its save flag is enabled. */
  private sharingDirty = false;
  private eventAvailDirty = false;
  private prefsDirty = false;

  protected readonly isNew = computed(() => !this.id() || this.id() === 'new');

  protected readonly form = this.fb.group({
    roomTypeId: [null as number | null, Validators.required],
    buildingId: [null as number | null],
    parentId: [null as number | null],
    name: ['', [Validators.required, Validators.maxLength(40)]],
    displayName: ['', Validators.maxLength(100)],
    externalId: ['', Validators.maxLength(40)],
    capacity: [null as number | null, Validators.required],
    controlDepartmentId: [null as number | null],
    x: [null as number | null],
    y: [null as number | null],
    area: [null as number | null],
    distanceCheck: [true],
    roomCheck: [true],
    examCapacity: [null as number | null],
    eventDepartmentId: [null as number | null],
    eventStatus: [null as number | null],
    eventNote: ['', Validators.maxLength(2048)],
    eventEmail: ['', Validators.maxLength(200)],
    breakTime: [null as number | null],
  });

  // ---- permission gates (from props for a new room, from the room on edit) ----
  private gate(propFlag?: boolean, roomFlag?: boolean): boolean {
    return this.isNew() ? !!propFlag : !!roomFlag;
  }
  protected readonly canExternalId = computed(() =>
    this.gate(this.props()?.canChangeExternalId, this.loaded?.canChangeExternalId),
  );
  protected readonly canControl = computed(() =>
    this.gate(this.props()?.canChangeControll, this.loaded?.canChangeControll),
  );
  protected readonly canEvent = computed(() =>
    this.gate(this.props()?.canChangeEventProperties, this.loaded?.canChangeEventProperties),
  );
  protected readonly canCourses = computed(() => !!this.props()?.canSeeCourses);
  // "Can see" gates drive the read-only fallbacks legacy shows when a field is
  // viewable but not editable (RoomEdit's `else if (isCanSee...)` branches).
  protected readonly canSeeExams = computed(() => !!this.props()?.canSeeExams);
  protected readonly canSeeEvents = computed(() => !!this.props()?.canSeeEvents);
  protected readonly canExamStatus = computed(() =>
    this.gate(this.props()?.canChangeExamStatus, this.loaded?.canChangeExamStatus),
  );
  protected readonly canEditRoomExams = computed(() => !!this.props()?.canEditRoomExams);
  protected readonly canPicture = computed(() =>
    this.gate(this.props()?.canChangePicture, this.loaded?.canChangePicture),
  );
  protected readonly canGroups = computed(
    () => this.gate(this.props()?.canChangeGroups, this.loaded?.canChangeGroups) && this.groups().length > 0,
  );
  protected readonly canFeatures = computed(
    () => this.gate(this.props()?.canChangeFeatures, this.loaded?.canChangeFeatures) && this.features().length > 0,
  );
  /** Room sharing is editable when the user manages departments or availability. */
  protected readonly canSharing = computed(
    () =>
      !!this.props()?.canEditDepartments ||
      this.gate(this.props()?.canChangeAvailability, this.loaded?.canChangeAvailability),
  );
  protected readonly canEventAvail = computed(() =>
    this.gate(this.props()?.canChangeEventAvailability, this.loaded?.canChangeEventAvailability),
  );

  // ---- selects / derived option lists ----
  protected readonly roomTypeOptions = computed<Option[]>(() =>
    (this.props()?.roomTypes ?? []).map((t) => ({ label: t.label ?? '', value: t.id! })),
  );
  protected readonly buildingOptions = computed<Option[]>(() =>
    (this.props()?.buildings ?? []).map((b) => ({ label: `${b.abbreviation} - ${b.name}`, value: b.id! })),
  );
  protected readonly controlDeptOptions = computed<Option[]>(() =>
    (this.props()?.departments ?? []).map((d) => ({ label: this.deptLabel(d, false), value: d.id! })),
  );
  protected readonly eventDeptOptions = computed<Option[]>(() =>
    (this.props()?.departments ?? [])
      .filter((d) => d.event)
      .map((d) => ({ label: this.deptLabel(d, true), value: d.id! })),
  );
  protected readonly eventStatusOptions: Option[] = EVENT_STATUS_NAMES.map((label, value) => ({ label, value }));

  private readonly selectedType = computed(() =>
    (this.props()?.roomTypes ?? []).find((t) => t.id === this.form.controls.roomTypeId.value),
  );
  /** A "room" type lives in a building and uses a room number; others are locations. */
  protected readonly isRoomType = computed(() => !!this.selectedType()?.room);
  protected readonly nameLabel = computed(() => (this.isRoomType() ? 'Room Number' : 'Room Name'));
  protected readonly areaUnit = computed(() => (this.props()?.roomAreaMetricUnits ? 'm²' : 'ft²'));
  /** An event room has an event department selected. */
  protected readonly isEventRoom = computed(() =>
    this.canEvent() ? this.eventDeptId() != null : this.loaded?.eventDepartment != null,
  );

  // ---- group / feature / exam / service groupings ----
  protected readonly groups = computed(() => this.props()?.groups ?? []);
  protected readonly features = computed(() => this.props()?.features ?? []);
  protected readonly examTypes = computed(() => this.props()?.examTypes ?? []);
  protected readonly pictureTypeOptions = computed<Option[]>(() =>
    (this.props()?.pictureTypes ?? []).map((t: AttachmentTypeInterface) => ({ label: t.label ?? '', value: t.id! })),
  );
  protected readonly globalGroups = computed(() => this.groups().filter((g) => !g.department));
  protected readonly deptGroups = computed(() => {
    const out: { label: string; groups: GroupInterface[] }[] = [];
    for (const d of this.props()?.departments ?? []) {
      const gs = this.groups().filter((g) => g.department?.id === d.id);
      if (gs.length) out.push({ label: this.deptLabel(d, false), groups: gs });
    }
    return out;
  });
  protected readonly globalFeatures = computed(() => this.features().filter((f) => !f.type));
  protected readonly typedFeatures = computed(() => {
    const out: { label: string; features: FeatureInterface[] }[] = [];
    for (const t of this.props()?.featureTypes ?? []) {
      const fs = this.features().filter((f) => f.type?.id === t.id);
      if (fs.length) out.push({ label: t.label ?? '', features: fs });
    }
    return out;
  });
  /** Event service providers visible for the selected event department. */
  protected readonly visibleServices = computed<EventServiceProviderInterface[]>(() => {
    const dept = this.eventDeptId();
    return (this.props()?.eventServiceProviders ?? []).filter(
      (s) => s.departmentId == null || s.departmentId === dept,
    );
  });
  /** Exam types the user selected, that have a loaded period-preference model. */
  protected readonly activePeriodModels = computed(() => {
    const out: { label: string; model: PeriodPreferenceModel }[] = [];
    const models = this.periodModels();
    const sel = this.examSel();
    for (const t of this.examTypes()) {
      if (t.id != null && sel[t.id] && models[t.id]?.periods?.length) {
        out.push({ label: t.label ?? '', model: models[t.id] });
      }
    }
    return out;
  });

  constructor() {
    effect(() => {
      this.id(); // re-run when the route id changes
      this.load();
    });
    this.form.controls.buildingId.valueChanges.subscribe((id) => this.buildingChanged(id));
    this.form.controls.eventDepartmentId.valueChanges.subscribe((id) => this.eventDeptId.set(id));
  }

  private deptLabel(d: RoomInterface_DepartmentInterface, event: boolean): string {
    const abbv = event ? d.code : d.externalAbbv || d.code;
    const label = event ? d.label : d.externalLabel || d.label;
    return `${abbv ?? ''} - ${label ?? ''}`;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.page.set(this.isNew() ? 'Add Room' : 'Edit Room');
    this.resetSubModels();

    const propsReq: RoomPropertiesRequest = {};
    this.rpc.execute<RoomPropertiesInterface>('RoomPropertiesRequest', propsReq).subscribe({
      next: (p) => {
        this.props.set(p);
        if (this.isNew()) {
          this.initNew();
          this.loadSubModels(null, p.session?.id ?? null);
          this.buildFutureRows();
          this.loading.set(false);
        } else {
          this.loadRoom(Number(this.id()), p.session?.id ?? null);
        }
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private resetSubModels(): void {
    this.sharingModel.set(null);
    this.eventAvailModel.set(null);
    this.periodModels.set({});
    this.pictures.set([]);
    this.futureRows.set([]);
    this.sharingDirty = this.eventAvailDirty = this.prefsDirty = false;
  }

  private loadRoom(roomId: number, sessionId: number | null): void {
    this.rooms.get(roomId, sessionId).subscribe({
      next: (room) => {
        if (!room) {
          this.error.set('Room not found.');
          this.loading.set(false);
          return;
        }
        this.loaded = room;
        this.populate(room);
        if (room.building?.id) this.loadParents(room.building.id, room.parent?.uniqueId ?? null);
        this.loadSubModels(room.uniqueId ?? null, sessionId);
        this.buildFutureRows();
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  /** Load room-sharing, event-availability, and per-exam-type period models. */
  private loadSubModels(locationId: number | null, sessionId: number | null): void {
    if (this.canSharing()) {
      this.rpc
        .execute<RoomSharingModel>('RoomSharingRequest', {
          operation: 'LOAD',
          sessionId: sessionId ?? undefined,
          locationId: locationId ?? undefined,
          eventAvailability: false,
          includeRoomPreferences: true,
        })
        .subscribe({ next: (m) => this.sharingModel.set(m), error: () => this.sharingModel.set(null) });
    }
    if (this.canEventAvail()) {
      this.rpc
        .execute<RoomSharingModel>('RoomSharingRequest', {
          operation: 'LOAD',
          sessionId: sessionId ?? undefined,
          locationId: locationId ?? undefined,
          eventAvailability: true,
        })
        .subscribe({ next: (m) => this.eventAvailModel.set(m), error: () => this.eventAvailModel.set(null) });
    }
    if (this.canEditRoomExams()) {
      for (const t of this.examTypes()) {
        if (t.id == null) continue;
        this.rpc
          .execute<PeriodPreferenceModel>('PeriodPreferenceRequest', {
            operation: 'LOAD',
            sourceId: locationId ?? undefined,
            examTypeId: t.id,
            sessionId: sessionId ?? undefined,
          })
          .subscribe({
            next: (m) => this.periodModels.update((cur) => ({ ...cur, [t.id!]: m })),
            error: () => {},
          });
      }
    }
  }

  /** Academic session shown at the top of the form (read-only). */
  protected readonly sessionLabel = computed(() => this.view()?.sessionName || this.props()?.session?.label || '');

  // ---- read-only display fallbacks (edit view, field viewable but not editable) ----
  protected controlDeptText(): string {
    const d = this.view()?.controlDepartment;
    return d ? this.deptLabel(d, false) : '';
  }
  protected eventDeptText(): string {
    const d = this.view()?.eventDepartment;
    return d ? this.deptLabel(d, true) : '';
  }
  protected eventStatusText(): string {
    const r = this.view();
    const s = r?.eventStatus ?? r?.defaultEventStatus;
    return s == null ? '' : (EVENT_STATUS_NAMES[s] ?? '');
  }
  protected eventNoteText(): string {
    return this.view()?.eventNote || this.view()?.defaultEventNote || '';
  }
  protected eventEmailText(): string {
    return this.view()?.eventEmail || this.view()?.defaultEventEmail || '';
  }
  protected breakTimeText(): string {
    const r = this.view();
    const b = r?.breakTime ?? r?.defaultBreakTime;
    return b == null ? '' : String(b);
  }
  protected examCapacityText(): string {
    const c = this.view()?.examCapacity;
    return c == null ? '' : String(c);
  }
  protected readonly viewGroups = computed(() => this.view()?.groups ?? []);
  protected readonly viewFeatures = computed(() => this.view()?.features ?? []);
  protected readonly viewExamTypes = computed(() => (this.view()?.examTypes ?? []).map((t) => t.label).join(', '));

  private initNew(): void {
    this.loaded = { sessionId: this.props()?.session?.id };
    this.view.set(null);
    this.groupSel = {};
    this.featureSel = {};
    this.examSel.set({});
    this.serviceSel = {};
    this.eventDeptId.set(null);
    const depts = this.props()?.departments ?? [];
    this.form.reset(
      {
        roomTypeId: null,
        buildingId: null,
        parentId: null,
        name: '',
        displayName: '',
        externalId: '',
        capacity: null,
        // Legacy preselects the sole controlling department.
        controlDepartmentId: depts.length === 1 ? (depts[0].id ?? null) : null,
        x: null,
        y: null,
        area: null,
        distanceCheck: true,
        roomCheck: true,
        examCapacity: null,
        eventDepartmentId: null,
        eventStatus: null,
        eventNote: '',
        eventEmail: '',
        breakTime: null,
      },
      { emitEvent: false },
    );
  }

  private populate(r: RoomDetailInterface): void {
    this.view.set(r);
    this.form.reset(
      {
        roomTypeId: r.roomType?.id ?? null,
        buildingId: r.building?.id ?? null,
        parentId: r.parent?.uniqueId ?? null,
        name: r.name ?? '',
        displayName: r.abbv ?? '',
        externalId: r.externalId ?? '',
        capacity: r.capacity ?? null,
        controlDepartmentId: r.controlDepartment?.id ?? null,
        x: r.x ?? null,
        y: r.y ?? null,
        area: r.area ?? null,
        distanceCheck: !r.ignoreTooFar,
        roomCheck: !r.ignoreRoomCheck,
        examCapacity: r.examCapacity ?? null,
        eventDepartmentId: r.eventDepartment?.id ?? null,
        eventStatus: r.eventStatus ?? null,
        eventNote: r.eventNote ?? '',
        eventEmail: r.eventEmail ?? '',
        breakTime: r.breakTime ?? null,
      },
      { emitEvent: false },
    );
    this.eventDeptId.set(r.eventDepartment?.id ?? null);
    this.groupSel = {};
    for (const g of r.groups ?? []) if (g.id != null) this.groupSel[g.id] = true;
    this.featureSel = {};
    for (const f of r.features ?? []) if (f.id != null) this.featureSel[f.id] = true;
    const exam: Record<number, boolean> = {};
    for (const e of r.examTypes ?? []) if (e.id != null) exam[e.id] = true;
    this.examSel.set(exam);
    this.serviceSel = {};
    for (const s of r.services ?? []) if (s.id != null) this.serviceSel[s.id] = true;
    this.pictures.set(r.pictures ?? []);
  }

  private buildFutureRows(): void {
    const rows: FutureRow[] = [];
    if (this.isNew()) {
      for (const s of this.props()?.futureSessions ?? []) {
        if (s.id == null) continue;
        rows.push(this.futureRow(-s.id, s.label ?? '', s.label ?? '', true));
      }
    } else {
      for (const fr of this.loaded?.futureRooms ?? []) {
        if (fr.id == null) continue;
        const label = fr.displayName ? `${fr.label} - ${fr.displayName}` : (fr.label ?? '');
        rows.push(this.futureRow(fr.id, label, fr.session?.label ?? '', false));
      }
    }
    this.futureRows.set(rows);
  }

  private futureRow(key: number, label: string, session: string, isNew: boolean): FutureRow {
    const ops: Record<number, boolean> = {};
    for (const op of FUTURE_OPS) ops[op.bit] = isNew ? op.defaultNew : op.defaultEdit;
    return { key, label, session, selected: isNew, ops };
  }

  private buildingChanged(buildingId: number | null): void {
    const b = (this.props()?.buildings ?? []).find((x) => x.id === buildingId);
    if (b) {
      // Match legacy: seed the coordinates from the building's location.
      this.form.controls.x.setValue(b.x ?? null);
      this.form.controls.y.setValue(b.y ?? null);
    }
    // The old partition belongs to the previous building — drop it.
    this.form.controls.parentId.setValue(null, { emitEvent: false });
    if (buildingId != null) this.loadParents(buildingId, null);
    else this.parentOptions.set([]);
  }

  private loadParents(buildingId: number, keep: number | null): void {
    const req: GetRoomsOfABuildingRequest = { buildingId };
    this.rpc.execute<RoomDetailInterface[]>('GetRoomsOfABuildingRequest', req).subscribe({
      next: (list) => {
        const self = this.loaded?.uniqueId;
        this.parentOptions.set(
          (list ?? [])
            // Exclude self and rooms that are themselves partitions of another room.
            .filter((r) => r.uniqueId !== self && !r.parent)
            .map((r) => ({ label: r.abbv ? `${r.name} - ${r.abbv}` : (r.name ?? ''), value: r.uniqueId! })),
        );
        if (keep != null) this.form.controls.parentId.setValue(keep);
      },
      error: () => this.parentOptions.set([]),
    });
  }

  toggleGroup(id: number, checked: boolean): void {
    this.groupSel = { ...this.groupSel, [id]: checked };
  }
  toggleFeature(id: number, checked: boolean): void {
    this.featureSel = { ...this.featureSel, [id]: checked };
  }
  toggleExam(id: number, checked: boolean): void {
    this.examSel.update((s) => ({ ...s, [id]: checked }));
  }
  toggleService(id: number, checked: boolean): void {
    this.serviceSel = { ...this.serviceSel, [id]: checked };
  }
  markSharingDirty(): void {
    this.sharingDirty = true;
  }
  markEventAvailDirty(): void {
    this.eventAvailDirty = true;
  }
  markPrefsDirty(): void {
    this.prefsDirty = true;
  }

  // ---- pictures ----
  protected readonly uploading = signal(false);

  setPictureType(pic: RoomPictureInterface, typeId: number): void {
    const t = (this.props()?.pictureTypes ?? []).find((x) => x.id === typeId);
    this.pictures.update((list) => list.map((p) => (p === pic ? { ...p, pictureType: t } : p)));
  }
  removePicture(pic: RoomPictureInterface): void {
    this.pictures.update((list) => list.filter((p) => p !== pic));
  }
  pictureUrl(pic: RoomPictureInterface): string {
    return `/picture?id=${pic.uniqueId}`;
  }
  /** Persisted pictures have a positive id and are served by the picture servlet. */
  isPersistedPicture(pic: RoomPictureInterface): boolean {
    return (pic.uniqueId ?? -1) > 0;
  }

  /**
   * Upload a chosen image: POST it to the UploadServlet, then register it as a
   * (temporary) room picture. The returned picture — with a negative temp id —
   * is appended to the list and persisted when the room is saved (PICTURES flag).
   */
  onPictureSelected(input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    const sid = this.props()?.session?.id ?? null;
    this.rooms.uploadPicture(file).subscribe({
      next: (msg) => {
        if (/^(ERROR|No file)/i.test(msg.trim())) {
          this.uploading.set(false);
          input.value = '';
          this.messages.add({ severity: 'error', summary: 'Upload failed', detail: msg });
          return;
        }
        this.rooms.registerUploadedPicture(sid, this.loaded?.uniqueId ?? null).subscribe({
          next: (res) => {
            this.uploading.set(false);
            input.value = '';
            const added = res.pictures ?? [];
            if (added.length) this.pictures.update((list) => [...list, ...added]);
          },
          error: (e: ApiError) => {
            this.uploading.set(false);
            input.value = '';
            this.messages.add({ severity: 'error', summary: 'Upload failed', detail: e.message });
          },
        });
      },
      error: (e: ApiError) => {
        this.uploading.set(false);
        input.value = '';
        this.messages.add({ severity: 'error', summary: 'Upload failed', detail: e.message });
      },
    });
  }

  // ---- future rooms table ----
  protected futureOpVisible(bit: number): boolean {
    return (this.saveFlags() & bit) !== 0;
  }
  toggleFutureRow(row: FutureRow, checked: boolean): void {
    this.futureRows.update((rows) => rows.map((r) => (r === row ? { ...r, selected: checked } : r)));
  }
  toggleFutureOp(row: FutureRow, bit: number, checked: boolean): void {
    this.futureRows.update((rows) =>
      rows.map((r) => (r === row ? { ...r, ops: { ...r.ops, [bit]: checked } } : r)),
    );
  }

  /** Field-group mask for the primary session — only groups this form populated. */
  private saveFlags(): number {
    const F = RoomsService.FLAG;
    let flags = F.ROOM_PROPERTIES;
    if (this.canExamStatus()) flags |= F.EXAM_PROPERTIES;
    if (this.canEvent()) flags |= F.EVENT_PROPERTIES;
    if (this.canGroups()) flags |= F.GROUPS;
    if (this.canFeatures()) flags |= F.FEATURES;
    if (this.sharingModel()) flags |= F.ROOM_SHARING;
    if (this.canEditRoomExams()) flags |= F.EXAM_PREFS;
    if (this.eventAvailModel() && this.isEventRoom()) flags |= F.EVENT_AVAILABILITY;
    if (this.canPicture()) flags |= F.PICTURES;
    return flags;
  }

  save(): void {
    const f = this.form;
    // Building is required only for a room-type location.
    const needBuilding = this.isRoomType();
    f.controls.buildingId.setErrors(needBuilding && f.controls.buildingId.value == null ? { required: true } : null);
    if (f.invalid) {
      f.markAllAsTouched();
      return;
    }
    const v = f.getRawValue();
    const p = this.props();
    const findType = (p?.roomTypes ?? []).find((t) => t.id === v.roomTypeId);
    const findBuilding = (p?.buildings ?? []).find((b) => b.id === v.buildingId);
    const findControl = (p?.departments ?? []).find((d) => d.id === v.controlDepartmentId);
    const findEventDept = (p?.departments ?? []).find((d) => d.id === v.eventDepartmentId);
    const parent = needBuilding && v.parentId != null ? this.parentOptionRoom(v.parentId) : undefined;

    const groups: GroupInterface[] = (p?.groups ?? []).filter((g) => g.id != null && this.groupSel[g.id]);
    const features: FeatureInterface[] = (p?.features ?? []).filter((x) => x.id != null && this.featureSel[x.id]);

    const room: RoomDetailInterface = {
      ...this.loaded,
      roomType: findType,
      building: needBuilding ? findBuilding : undefined,
      parent,
      name: v.name ?? '',
      abbv: v.displayName || undefined,
      externalId: this.canExternalId() ? v.externalId || undefined : this.loaded?.externalId,
      capacity: v.capacity ?? undefined,
      controlDepartment: this.canControl() ? findControl : this.loaded?.controlDepartment,
      x: v.x ?? undefined,
      y: v.y ?? undefined,
      area: v.area ?? undefined,
      ignoreTooFar: this.canCourses() ? !v.distanceCheck : this.loaded?.ignoreTooFar,
      ignoreRoomCheck: !v.roomCheck,
      groups,
      features,
    };

    if (this.canExamStatus()) {
      room.examCapacity = v.examCapacity ?? undefined;
      const sel = this.examSel();
      room.examTypes = this.examTypes().filter((t) => t.id != null && sel[t.id]);
    }
    if (this.canEvent()) {
      room.eventDepartment = findEventDept;
      room.eventStatus = v.eventStatus ?? undefined;
      room.eventNote = v.eventNote || undefined;
      room.eventEmail = v.eventEmail || undefined;
      room.breakTime = v.breakTime ?? undefined;
      room.services = this.isEventRoom()
        ? this.visibleServices().filter((s) => s.id != null && this.serviceSel[s.id])
        : [];
    }
    if (this.sharingModel()) room.roomSharingModel = this.sharingModel()!;
    if (this.eventAvailModel() && this.isEventRoom()) room.eventAvailabilityModel = this.eventAvailModel()!;
    if (this.canEditRoomExams()) {
      const map: Record<number, PeriodPreferenceModel> = {};
      const models = this.periodModels();
      const sel = this.examSel();
      for (const t of this.examTypes()) {
        if (t.id != null && sel[t.id] && models[t.id]) map[t.id] = models[t.id];
      }
      room.periodPreferenceModels = map;
    }
    if (this.canPicture()) room.pictures = this.pictures();

    const futureFlags: Record<string, number> = { '0': this.saveFlags() };
    for (const row of this.futureRows()) {
      if (!row.selected) continue;
      let flags = 0;
      for (const op of FUTURE_OPS) if (row.ops[op.bit]) flags |= op.bit;
      futureFlags[String(row.key)] = flags;
    }

    this.saving.set(true);
    this.rooms.save(room, this.props()?.session?.id ?? null, futureFlags).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.isNew() ? 'Room created' : 'Room saved',
          detail: res.name,
        });
        this.router.navigate(['/rooms']);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.error.set(e.message);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  private parentOptionRoom(id: number): RoomDetailInterface {
    const opt = this.parentOptions().find((o) => o.value === id);
    return { uniqueId: id, name: opt?.label };
  }

  confirmDelete(): void {
    this.confirm.confirm({
      header: 'Delete room',
      message: 'Delete this room? This cannot be undone.',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(),
    });
  }

  private doDelete(): void {
    const uid = this.loaded?.uniqueId;
    if (uid == null) return;
    this.saving.set(true);
    this.rooms.remove(uid, this.props()?.session?.id ?? null).subscribe({
      next: () => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Room deleted' });
        this.router.navigate(['/rooms']);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
      },
    });
  }

  protected readonly futureOps = FUTURE_OPS;
  protected readonly canDelete = computed(() => !this.isNew() && !!this.loaded?.canDelete);

  back(): void {
    this.router.navigate(['/rooms']);
  }
}
