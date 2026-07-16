import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { EventGrid } from './event-grid';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSession,
  ApiError,
  ContactInterface,
  Entity,
  EventDetailRpcRequest,
  EventInterface,
  EventPropertiesRpcRequest,
  EventPropertiesRpcResponse,
  EventType,
  EventRoomAvailabilityRpcRequest,
  EventRoomAvailabilityRpcResponse,
  FilterRpcResponse,
  MeetingConflictInterface,
  MeetingInterface,
  RoomFilterRpcRequest,
  RPC_ROOM_FILTER,
  SaveEventRpcRequest,
  SaveOrApproveEventRpcResponse,
  SessionMonth,
  SponsoringOrganizationInterface,
} from '../../core/models';

/** A room usable by the meeting builder — enriched from the events room enumeration. */
interface RoomPick {
  id: number; // Location.uniqueId (client-side key)
  permId: number | null; // Location.permanentId (what the availability backend keys on)
  label: string;
  breakTime: number;
  roomType?: string;
  capacity?: number;
}

/** A selectable session date, normalized to the backend day-of-year. */
interface SelectableDate {
  dayOfYear: number;
  iso: string; // yyyy-MM-dd
  label: string; // "Mon 01/05"
  dayOfWeek: number; // 0=Mon..6=Sun (UniTime)
  holiday: boolean;
  weekend: boolean;
  past: boolean;
}

// SessionMonth.Flag ordinals (see EventInterface.SessionMonth.Flag) — bit = 1<<ordinal.
const F_START = 1 << 0;
const F_HOLIDAY = 1 << 3;
const F_BREAK = 1 << 4;
const F_DISABLED = 1 << 6;
const F_PAST = 1 << 7;
const F_WEEKEND = 1 << 8;

const MS_PER_DAY = 86400000;

/**
 * Event Add / Edit — functional core port of gwt/client/events/EventAdd.java, now
 * with the full interactive meeting builder ported from AddMeetingsDialog.java.
 *
 * Route "event-add" creates a new event; "event-add?id=<eventId>" edits an existing
 * one (EventDetailRpcRequest -> EventInterface). Form options come from
 * EventPropertiesRpcRequest; save goes through SaveEventRpcRequest (>=1 meeting
 * required). Sessions from ListAcademicSessions; rooms from RPC_ROOM_FILTER enumerate.
 *
 * Meeting builder (AddMeetingsDialog flow): pick session dates (RequestSessionDetails
 * -> SessionMonth[]), start/end time, and rooms; "Check Availability" runs
 * EventRoomAvailabilityRpcRequest and renders a dates x rooms matrix; available cells
 * are pre-selected, conflicted cells show a count + tooltip and stay selectable;
 * "Add Meetings" builds MeetingInterface[] from the selected cells.
 *
 * NOTE on the availability backend: EventRoomAvailabilityBackend queries by
 * Location.permanentId and keys its `overlaps` map by permanentId — so `locations`
 * carries room permIds (params.permId from the enumeration) and cell conflicts are
 * looked up by permId (falling back to uniqueId when a permId is absent).
 */
@Component({
  selector: 'app-event-add',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    CardModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    DialogModule,
    TableModule,
    TagModule,
    MessageModule,
    TooltipModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './event-add.html',
  styles: [
    `
      .mb-toolbar { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 0.5rem; }
      .mb-toolbar .spacer { flex: 1 1 auto; }
      .mb-step { display: flex; flex-direction: column; gap: 1rem; min-width: 420px; }
      .mb-hint { color: var(--text-color-secondary, #6c757d); font-size: 0.85rem; margin: 0; }
      .mb-legend { display: flex; gap: 1rem; flex-wrap: wrap; font-size: 0.8rem; align-items: center; }
      .mb-legend .sw { display: inline-block; width: 14px; height: 14px; border-radius: 3px; margin-right: 4px; vertical-align: middle; border: 1px solid rgba(0,0,0,0.15); }
      .sw.free { background: #e6f4ea; }
      .sw.conflict { background: #fdecea; }
      .sw.sel { background: #cfe8ff; }

      .mb-matrix-wrap { overflow-x: auto; max-height: 60vh; overflow-y: auto; }
      table.mb-matrix { border-collapse: collapse; font-size: 0.8rem; }
      table.mb-matrix th, table.mb-matrix td { border: 1px solid var(--surface-border, #dee2e6); padding: 0; text-align: center; }
      table.mb-matrix th.corner { background: var(--surface-100, #f8f9fa); position: sticky; left: 0; z-index: 2; }
      table.mb-matrix th.room-h { min-width: 84px; padding: 4px 6px; background: var(--surface-100, #f8f9fa); cursor: pointer; }
      table.mb-matrix th.room-h small { display: block; font-weight: 400; color: var(--text-color-secondary, #6c757d); }
      table.mb-matrix th.date-h { white-space: nowrap; padding: 4px 8px; text-align: right; background: var(--surface-50, #fcfcfd); cursor: pointer; position: sticky; left: 0; z-index: 1; }
      table.mb-matrix td.cell { width: 84px; height: 34px; cursor: pointer; user-select: none; }
      td.cell .inner { display: flex; align-items: center; justify-content: center; height: 34px; }
      td.cell.free { background: #e6f4ea; }
      td.cell.conflict { background: #fdecea; color: #b3261e; font-weight: 600; }
      td.cell.selected { outline: 3px solid #2684ff; outline-offset: -3px; background: #cfe8ff; }
      td.cell.selected.conflict { background: #f7cfca; }
      td.cell:hover { filter: brightness(0.96); }
      .conf-badge { font-weight: 700; }
    `,
  ],
})
export class EventAdd {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private router = inject(Router);

  /** Optional event id from the `?id=` query param (component input binding). */
  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly props = signal<EventPropertiesRpcResponse | null>(null);
  protected readonly sessions = signal<AcademicSession[]>([]);
  protected sessionId: number | null = null;

  /** Event types the user may add, derived from the properties permission flags. */
  protected readonly typeOptions = signal<{ label: string; value: EventType }[]>([]);
  protected readonly sponsorOptions = computed<SponsoringOrganizationInterface[]>(
    () => this.props()?.sponsoringOrganizations ?? [],
  );
  protected readonly standardNotes = computed(() => this.props()?.standardNotes ?? []);

  /** The full loaded event (edit mode); non-rendered fields are preserved on save. */
  private loaded: EventInterface | null = null;
  protected readonly isEdit = computed(() => !!this.id());

  /** Working meeting list (loaded + newly added). */
  protected readonly meetings = signal<MeetingInterface[]>([]);
  /** A synthetic event so app-event-grid can render the working meetings. */
  protected readonly gridEvents = computed<EventInterface[]>(() => [
    {
      eventId: -1,
      eventName: this.form.controls.eventName.value || 'New Event',
      eventType: this.form.controls.eventType.value ?? 'Special',
      meetings: this.meetings(),
    },
  ]);

  // --- room enumeration (shared: builder input) ---
  protected readonly rooms = signal<RoomPick[]>([]);
  protected readonly loadingRooms = signal(false);

  // --- meeting builder state ---
  protected readonly builderVisible = signal(false);
  protected readonly builderStep = signal<'dates' | 'grid'>('dates');
  protected readonly loadingDates = signal(false);
  protected readonly checking = signal(false);

  private sessionMonths: SessionMonth[] = [];
  private sessionYear = 1900;
  protected readonly allowPast = signal(false);
  protected readonly selectableDates = signal<SelectableDate[]>([]);
  /** dayOfYear -> date, for labeling meetings built from the matrix. */
  private dateByDoy = new Map<number, SelectableDate>();

  // builder step-1 selections (ngModel)
  protected selDateDoys: number[] = [];
  protected selRoomIds: number[] = [];
  protected startSlot: number | null = 8 * 12; // 8:00 default
  protected endSlot: number | null = 9 * 12; // 9:00 default

  // builder step-2 snapshot (taken at Check Availability)
  private availability: EventRoomAvailabilityRpcResponse | null = null;
  protected readonly gridDates = signal<SelectableDate[]>([]);
  protected readonly gridRooms = signal<RoomPick[]>([]);
  private gridStart = 0;
  private gridEnd = 0;
  /** selected matrix cells, key `${dayOfYear}:${roomId}`. */
  protected readonly selectedCells = signal<Set<string>>(new Set());

  /** 15-minute time options 7:00–22:00; value = 5-minute slot since midnight. */
  protected readonly timeOptions: { label: string; value: number }[] = (() => {
    const out: { label: string; value: number }[] = [];
    for (let min = 7 * 60; min <= 22 * 60; min += 15) {
      out.push({ label: this.fmtMin(min), value: min / 5 });
    }
    return out;
  })();

  protected standardNoteRef: string | null = null;

  protected readonly form = this.fb.group({
    eventName: ['', [Validators.required, Validators.maxLength(100)]],
    eventType: ['Special' as EventType, Validators.required],
    sponsorId: [null as number | null],
    maxCapacity: [null as number | null],
    eventEmail: [''],
    expirationDate: [''],
    firstName: [''],
    middleName: [''],
    lastName: [''],
    academicTitle: [''],
    email: [''],
    phone: [''],
    notes: [''],
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.init(id ? Number(id) : null);
    });
  }

  private init(eventId: number | null): void {
    this.loading.set(true);
    this.error.set(null);
    this.page.set(eventId ? 'Edit Event' : 'Add Event');

    this.rpc.execute<AcademicSession[]>('ListAcademicSessions', {}).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.selected) ?? sessions[0])?.uniqueId ?? null;
        this.loadProperties(eventId);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private loadProperties(eventId: number | null): void {
    const req: EventPropertiesRpcRequest = { pageName: 'add-event', sessionId: this.sessionId ?? undefined };
    this.rpc.execute<EventPropertiesRpcResponse>('EventPropertiesRpcRequest', req).subscribe({
      next: (p) => {
        this.props.set(p);
        this.buildTypeOptions(p);
        this.loadRooms();
        if (eventId) {
          this.loadEvent(eventId);
        } else {
          this.applyMainContact(p.mainContact);
          this.loading.set(false);
        }
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private buildTypeOptions(p: EventPropertiesRpcResponse): void {
    const opts: { label: string; value: EventType }[] = [];
    if (p.canAddEvent) opts.push({ label: 'Special Event', value: 'Special' });
    if (p.canAddCourseEvent) opts.push({ label: 'Course Related Event', value: 'Course' });
    if (p.canAddUnavailableEvent) opts.push({ label: 'Not Available', value: 'Unavailabile' });
    if (!opts.length) opts.push({ label: 'Special Event', value: 'Special' });
    this.typeOptions.set(opts);
    if (!opts.some((o) => o.value === this.form.controls.eventType.value)) {
      this.form.controls.eventType.setValue(opts[0].value);
    }
  }

  private applyMainContact(c?: ContactInterface): void {
    if (!c) return;
    this.form.patchValue({
      firstName: c.firstName ?? '',
      middleName: c.middleName ?? '',
      lastName: c.lastName ?? '',
      academicTitle: c.title ?? '',
      email: c.email ?? '',
      phone: c.phone ?? '',
    });
  }

  private loadEvent(eventId: number): void {
    const req: EventDetailRpcRequest = { eventId, sessionId: this.sessionId ?? undefined };
    this.rpc.execute<EventInterface>('EventDetailRpcRequest', req).subscribe({
      next: (ev) => {
        this.loaded = ev ?? {};
        const c = ev.contact ?? {};
        this.form.reset({
          eventName: ev.eventName ?? '',
          eventType: (ev.eventType as EventType) ?? 'Special',
          sponsorId: ev.sponsor?.uniqueId ?? null,
          maxCapacity: ev.maxCapacity ?? null,
          eventEmail: ev.eventEmail ?? '',
          expirationDate: ev.expirationDate ?? '',
          firstName: c.firstName ?? '',
          middleName: c.middleName ?? '',
          lastName: c.lastName ?? '',
          academicTitle: c.title ?? '',
          email: c.email ?? '',
          phone: c.phone ?? '',
          notes: '',
        });
        this.meetings.set([...(ev.meetings ?? [])]);
        this.loading.set(false);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private loadRooms(): void {
    this.loadingRooms.set(true);
    const req: RoomFilterRpcRequest = { command: 'ENUMERATE', options: {}, sessionId: this.sessionId ?? undefined };
    this.rpc.execute<FilterRpcResponse>(RPC_ROOM_FILTER, req).subscribe({
      next: (res) => {
        this.rooms.set(
          (res.entities?.['results'] ?? []).map((e: Entity) => this.toRoomPick(e)),
        );
        this.loadingRooms.set(false);
      },
      error: () => this.loadingRooms.set(false),
    });
  }

  private toRoomPick(e: Entity): RoomPick {
    const p = e.params ?? {};
    const permId = p['permId'] != null && p['permId'] !== '' ? Number(p['permId']) : null;
    const breakTime = Number(p['breakTime'] ?? 0) || 0;
    const capacity = p['capacity'] != null ? Number(p['capacity']) : undefined;
    return {
      id: e.uniqueId!,
      permId: permId != null && !Number.isNaN(permId) ? permId : null,
      label: e.name ?? '',
      breakTime,
      roomType: p['type'],
      capacity: Number.isNaN(capacity as number) ? undefined : capacity,
    };
  }

  appendStandardNote(): void {
    if (!this.standardNoteRef) return;
    const cur = this.form.controls.notes.value ?? '';
    const sep = cur && !cur.endsWith('\n') ? '\n' : '';
    this.form.controls.notes.setValue(cur + sep + this.standardNoteRef);
    this.standardNoteRef = null;
  }

  // ------------------------------------------------------------------
  //  Meeting builder
  // ------------------------------------------------------------------

  openBuilder(): void {
    if (!this.rooms().length && !this.loadingRooms()) {
      this.messages.add({ severity: 'warn', summary: 'No rooms', detail: 'No event rooms are available in this session.' });
      return;
    }
    this.builderStep.set('dates');
    this.availability = null;
    this.selectedCells.set(new Set());
    this.builderVisible.set(true);
    if (!this.sessionMonths.length) this.loadSessionDates();
  }

  private loadSessionDates(): void {
    if (this.sessionId == null) return;
    this.loadingDates.set(true);
    this.rpc.execute<SessionMonth[]>('RequestSessionDetails', { sessionId: this.sessionId }).subscribe({
      next: (months) => {
        this.sessionMonths = months ?? [];
        this.sessionYear = this.computeSessionYear(this.sessionMonths);
        this.rebuildSelectableDates();
        this.loadingDates.set(false);
      },
      error: (e: ApiError) => {
        this.loadingDates.set(false);
        this.messages.add({ severity: 'error', summary: 'Dates unavailable', detail: e.message });
      },
    });
  }

  /** iSessionYear = year of the SessionMonth whose days carry the START flag. */
  private computeSessionYear(months: SessionMonth[]): number {
    for (const m of months) {
      const days = m.days ?? [];
      if (days.some((d) => (d & F_START) !== 0)) return m.year ?? 1900;
    }
    return months[0]?.year ?? 1900;
  }

  onAllowPastChange(): void {
    this.rebuildSelectableDates();
  }

  private rebuildSelectableDates(): void {
    const allowPast = this.allowPast();
    const out: SelectableDate[] = [];
    this.dateByDoy.clear();
    for (const m of this.sessionMonths) {
      const year = m.year ?? 0;
      const month0 = m.month ?? 0;
      const days = m.days ?? [];
      const dim = this.daysInMonth(year, month0);
      for (let di = 0; di < dim; di++) {
        const flags = di < days.length ? days[di] : 0;
        if ((flags & F_DISABLED) !== 0) continue;
        const past = (flags & F_PAST) !== 0;
        if (past && !allowPast) continue;
        const doy = this.dayOfYear(year, month0, di);
        const day = di + 1;
        const dow = (this.jsDayOfWeek(year, month0, day) + 6) % 7; // Mon=0
        const iso = `${year}-${this.pad(month0 + 1)}-${this.pad(day)}`;
        const sd: SelectableDate = {
          dayOfYear: doy,
          iso,
          label: `${['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][dow]} ${this.pad(month0 + 1)}/${this.pad(day)}`,
          dayOfWeek: dow,
          holiday: (flags & (F_HOLIDAY | F_BREAK)) !== 0,
          weekend: (flags & F_WEEKEND) !== 0,
          past,
        };
        out.push(sd);
        this.dateByDoy.set(doy, sd);
      }
    }
    out.sort((a, b) => a.dayOfYear - b.dayOfYear);
    this.selectableDates.set(out);
    // drop selections that are no longer valid
    const valid = new Set(out.map((d) => d.dayOfYear));
    this.selDateDoys = this.selDateDoys.filter((d) => valid.has(d));
  }

  /** Port of SessionDatesSelector.dayOfYear (normalized to iSessionYear). month0 = 0-based. */
  private dayOfYear(year: number, month0: number, dayIndex: number): number {
    let doy = Math.round((Date.UTC(year, month0, dayIndex + 1) - Date.UTC(year, 0, 1)) / MS_PER_DAY) + 1;
    if (year < this.sessionYear) doy -= this.daysInYear(year);
    else if (year > this.sessionYear) doy += this.daysInYear(this.sessionYear);
    return doy;
  }

  private jsDayOfWeek(year: number, month0: number, day: number): number {
    return new Date(Date.UTC(year, month0, day)).getUTCDay(); // 0=Sun..6=Sat
  }

  private daysInMonth(year: number, month0: number): number {
    return new Date(Date.UTC(year, month0 + 1, 0)).getUTCDate();
  }

  private daysInYear(year: number): number {
    return (year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)) ? 366 : 365;
  }

  private pad(n: number): string {
    return n < 10 ? '0' + n : '' + n;
  }

  private fmtMin(min: number): string {
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }

  checkAvailability(): void {
    if (!this.selDateDoys.length) {
      this.messages.add({ severity: 'warn', summary: 'No dates', detail: 'Select at least one date.' });
      return;
    }
    if (this.startSlot == null || this.endSlot == null || this.endSlot <= this.startSlot) {
      this.messages.add({ severity: 'warn', summary: 'Invalid time', detail: 'End time must be after start time.' });
      return;
    }
    if (!this.selRoomIds.length) {
      this.messages.add({ severity: 'warn', summary: 'No rooms', detail: 'Select at least one room.' });
      return;
    }

    const dates = [...this.selDateDoys].sort((a, b) => a - b);
    const pickedRooms = this.selRoomIds
      .map((id) => this.rooms().find((r) => r.id === id))
      .filter((r): r is RoomPick => !!r);
    // Availability backend keys on permanentId; fall back to uniqueId if a permId is missing.
    const locations = pickedRooms.map((r) => r.permId ?? r.id);
    const snapDates = dates
      .map((d) => this.dateByDoy.get(d))
      .filter((d): d is SelectableDate => !!d);

    const req: EventRoomAvailabilityRpcRequest = {
      sessionId: this.sessionId ?? undefined,
      eventId: this.id() ? Number(this.id()) : undefined,
      eventType: (this.form.controls.eventType.value ?? 'Special') as EventType,
      startSlot: this.startSlot,
      endSlot: this.endSlot,
      dates,
      locations,
    };

    this.checking.set(true);
    this.rpc.execute<EventRoomAvailabilityRpcResponse>('EventRoomAvailabilityRpcRequest', req).subscribe({
      next: (res) => {
        this.checking.set(false);
        this.availability = res ?? {};
        this.gridStart = this.startSlot!;
        this.gridEnd = this.endSlot!;
        this.gridDates.set(snapDates);
        this.gridRooms.set(pickedRooms);
        // default: select every available (conflict-free) cell.
        const sel = new Set<string>();
        for (const d of snapDates) {
          for (const r of pickedRooms) {
            if (this.conflictsFor(d.dayOfYear, r).length === 0) sel.add(this.cellKey(d.dayOfYear, r.id));
          }
        }
        this.selectedCells.set(sel);
        this.builderStep.set('grid');
      },
      error: (e: ApiError) => {
        this.checking.set(false);
        this.messages.add({ severity: 'error', summary: 'Availability check failed', detail: e.message });
      },
    });
  }

  backToDates(): void {
    this.builderStep.set('dates');
  }

  private cellKey(doy: number, roomId: number): string {
    return `${doy}:${roomId}`;
  }

  /** overlaps[dayOfYear][permId] — the conflicts for one (date, room) cell. */
  conflictsFor(doy: number, room: RoomPick): MeetingConflictInterface[] {
    const byDate = this.availability?.overlaps?.[String(doy)];
    if (!byDate) return [];
    const key = room.permId != null ? String(room.permId) : String(room.id);
    return byDate[key] ?? [];
  }

  cellConflictCount(doy: number, room: RoomPick): number {
    return this.conflictsFor(doy, room).length;
  }

  cellTooltip(doy: number, room: RoomPick): string {
    const conf = this.conflictsFor(doy, room);
    if (!conf.length) return 'Available';
    return conf
      .map((c) => `${c.eventName ?? 'Event'} (${this.slotLabel(c.startSlot)}–${this.slotLabel(c.endSlot)})`)
      .join('\n');
  }

  isCellSelected(doy: number, roomId: number): boolean {
    return this.selectedCells().has(this.cellKey(doy, roomId));
  }

  toggleCell(doy: number, roomId: number): void {
    this.selectedCells.update((set) => {
      const next = new Set(set);
      const k = this.cellKey(doy, roomId);
      if (next.has(k)) next.delete(k);
      else next.add(k);
      return next;
    });
  }

  /** Date row header: select all if any unselected, else clear the whole row. */
  toggleDateRow(doy: number): void {
    const roomsList = this.gridRooms();
    const anyOff = roomsList.some((r) => !this.isCellSelected(doy, r.id));
    this.selectedCells.update((set) => {
      const next = new Set(set);
      for (const r of roomsList) {
        const k = this.cellKey(doy, r.id);
        if (anyOff) next.add(k);
        else next.delete(k);
      }
      return next;
    });
  }

  /** Room column header: select all dates for the room, else clear the whole column. */
  toggleRoomCol(roomId: number): void {
    const datesList = this.gridDates();
    const anyOff = datesList.some((d) => !this.isCellSelected(d.dayOfYear, roomId));
    this.selectedCells.update((set) => {
      const next = new Set(set);
      for (const d of datesList) {
        const k = this.cellKey(d.dayOfYear, roomId);
        if (anyOff) next.add(k);
        else next.delete(k);
      }
      return next;
    });
  }

  protected readonly selectedCellCount = computed(() => this.selectedCells().size);

  addMeetingsFromGrid(): void {
    const sel = this.selectedCells();
    if (!sel.size) {
      this.messages.add({ severity: 'warn', summary: 'Nothing selected', detail: 'Select at least one date/room cell.' });
      return;
    }
    const built: MeetingInterface[] = [];
    for (const d of this.gridDates()) {
      for (const r of this.gridRooms()) {
        if (!sel.has(this.cellKey(d.dayOfYear, r.id))) continue;
        const conflicts = this.conflictsFor(d.dayOfYear, r);
        built.push({
          dayOfYear: d.dayOfYear,
          startSlot: this.gridStart,
          endSlot: this.gridEnd,
          startOffset: 0,
          endOffset: -(r.breakTime ?? 0),
          dayOfWeek: d.dayOfWeek,
          meetingDate: d.iso + 'T00:00:00Z',
          location: {
            resourceType: 'ROOM',
            resourceId: r.id,
            resourceName: r.label,
            roomType: r.roomType,
            size: r.capacity,
            breakTime: r.breakTime,
          },
          conflicts: conflicts.length ? conflicts : undefined,
          approvalStatus: 'Pending',
        });
      }
    }
    this.meetings.update((list) => [...list, ...built]);
    this.messages.add({ severity: 'success', summary: 'Meetings added', detail: `${built.length} meeting(s) added.` });
    this.builderVisible.set(false);
  }

  removeMeeting(m: MeetingInterface): void {
    this.meetings.update((list) => list.filter((x) => x !== m));
  }

  slotLabel(slot?: number): string {
    if (slot == null) return '';
    return this.fmtMin(5 * slot);
  }

  meetingDay(m: MeetingInterface): string {
    if (m.meetingDate) return m.meetingDate.split('T')[0];
    if (m.dayOfYear != null) {
      const sd = this.dateByDoy.get(m.dayOfYear);
      if (sd) return sd.iso;
      return 'day ' + m.dayOfYear;
    }
    return '';
  }

  meetingConflictCount(m: MeetingInterface): number {
    return m.conflicts?.length ?? 0;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.meetings().length) {
      this.messages.add({
        severity: 'warn',
        summary: 'No meetings',
        detail: 'Add at least one meeting — the event cannot be saved without meetings.',
      });
      return;
    }
    const v = this.form.getRawValue();
    const sponsor = v.sponsorId != null ? this.sponsorOptions().find((s) => s.uniqueId === v.sponsorId) : undefined;

    const contact: ContactInterface = {
      ...(this.loaded?.contact ?? {}),
      externalId: this.loaded?.contact?.externalId ?? this.props()?.mainContact?.externalId,
      firstName: v.firstName || undefined,
      middleName: v.middleName || undefined,
      lastName: v.lastName || undefined,
      title: v.academicTitle || undefined,
      email: v.email || undefined,
      phone: v.phone || undefined,
    };

    const event: EventInterface = {
      ...(this.loaded ?? {}),
      eventId: this.id() ? Number(this.id()) : undefined,
      eventName: v.eventName ?? '',
      eventType: v.eventType ?? 'Special',
      eventEmail: v.eventEmail || undefined,
      expirationDate: v.expirationDate || undefined,
      maxCapacity: v.maxCapacity ?? undefined,
      contact,
      sponsor: sponsor ?? undefined,
      meetings: this.meetings(),
    };

    const request: SaveEventRpcRequest = {
      event,
      sessionId: this.sessionId ?? undefined,
      message: v.notes || undefined,
      emailConfirmation: false,
    };

    this.saving.set(true);
    this.rpc.execute<SaveOrApproveEventRpcResponse>('SaveEventRpcRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        const warn = (res.messages ?? []).find((m) => m.level === 'ERROR' || m.level === 'WARN');
        if (warn) {
          this.messages.add({ severity: 'warn', summary: 'Saved with warnings', detail: warn.message });
        } else {
          this.messages.add({ severity: 'success', summary: 'Event saved', detail: res.event?.eventName });
        }
        this.router.navigate(['/events']);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  back(): void {
    this.router.navigate(['/events']);
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.loading.set(false);
  }
}
