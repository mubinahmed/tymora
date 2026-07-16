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
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
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
  FilterRpcResponse,
  MeetingInterface,
  RoomFilterRpcRequest,
  RPC_ROOM_FILTER,
  SaveEventRpcRequest,
  SaveOrApproveEventRpcResponse,
  SponsoringOrganizationInterface,
} from '../../core/models';

interface RoomPick {
  id: number;
  label: string;
}

/**
 * Event Add / Edit — functional core port of gwt/client/events/EventAdd.java.
 *
 * Route "event-add" creates a new event; "event-add?id=<eventId>" loads an
 * existing one (EventDetailRpcRequest -> EventInterface) and edits it. Form
 * options (addable event types, sponsoring organizations, prefilled main
 * contact, standard notes) come from EventPropertiesRpcRequest. Save goes
 * through SaveEventRpcRequest, whose server-side operation is derived from the
 * event: meetings present + no id => CREATE, + id => UPDATE, no meetings =>
 * DELETE. Because of that, at least one meeting is required to create/update —
 * a minimal meeting builder (room + date + start/end time, reusing the events
 * room enumeration) supplies real MeetingInterface rows, and app-event-grid
 * renders the resulting weekly timetable.
 *
 * Deferred vs. the GWT screen: the full AddMeetingsDialog (multi-date patterns,
 * room availability/conflict pre-check, matrix picker), additional-contact
 * people lookup, file attachment, related-course selection, and per-meeting
 * approve/cancel workflow. Persisted meetings are shown read-only and preserved
 * on save; removing a row drops it from the request.
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
    TableModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './event-add.html',
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
    { eventId: -1, eventName: this.form.controls.eventName.value || 'New Event', eventType: this.form.controls.eventType.value ?? 'Special', meetings: this.meetings() },
  ]);

  // --- meeting builder inputs ---
  protected readonly rooms = signal<RoomPick[]>([]);
  protected readonly loadingRooms = signal(false);
  protected newRoomId: number | null = null;
  protected newDate = '';
  protected newStartSlot: number | null = null;
  protected newEndSlot: number | null = null;

  /** Half-hour time options (07:00–22:00), value = 5-minute slot since midnight. */
  protected readonly timeOptions: { label: string; value: number }[] = (() => {
    const out: { label: string; value: number }[] = [];
    for (let min = 7 * 60; min <= 22 * 60; min += 30) {
      const h = Math.floor(min / 60);
      const m = min % 60;
      out.push({ label: `${h}:${m < 10 ? '0' : ''}${m}`, value: min / 5 });
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
          (res.entities?.['results'] ?? []).map((e: Entity) => ({ id: e.uniqueId!, label: e.name ?? '' })),
        );
        this.loadingRooms.set(false);
      },
      error: () => this.loadingRooms.set(false),
    });
  }

  appendStandardNote(): void {
    if (!this.standardNoteRef) return;
    const cur = this.form.controls.notes.value ?? '';
    const sep = cur && !cur.endsWith('\n') ? '\n' : '';
    this.form.controls.notes.setValue(cur + sep + this.standardNoteRef);
    this.standardNoteRef = null;
  }

  addMeeting(): void {
    if (this.newRoomId == null || !this.newDate || this.newStartSlot == null || this.newEndSlot == null) return;
    if (this.newEndSlot <= this.newStartSlot) {
      this.messages.add({ severity: 'warn', summary: 'Invalid time', detail: 'End time must be after start time.' });
      return;
    }
    const room = this.rooms().find((r) => r.id === this.newRoomId);
    const d = new Date(this.newDate + 'T00:00:00');
    const meeting: MeetingInterface = {
      location: { resourceId: this.newRoomId, resourceName: room?.label, resourceType: 'ROOM' },
      meetingDate: this.newDate + 'T00:00:00Z',
      dayOfWeek: (d.getDay() + 6) % 7, // JS 0=Sun..6=Sat -> UniTime 0=Mon..6=Sun
      startSlot: this.newStartSlot,
      endSlot: this.newEndSlot,
      startOffset: 0,
      endOffset: 0,
      approvalStatus: 'Pending',
    };
    this.meetings.update((list) => [...list, meeting]);
    this.newDate = '';
    this.newStartSlot = null;
    this.newEndSlot = null;
  }

  removeMeeting(m: MeetingInterface): void {
    this.meetings.update((list) => list.filter((x) => x !== m));
  }

  slotLabel(slot?: number): string {
    if (slot == null) return '';
    const min = 5 * slot;
    const h = Math.floor(min / 60);
    const mm = min % 60;
    return `${h}:${mm < 10 ? '0' : ''}${mm}`;
  }

  meetingDay(m: MeetingInterface): string {
    return (m.meetingDate ?? '').split('T')[0];
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
