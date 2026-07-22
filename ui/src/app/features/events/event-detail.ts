import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { EventGrid } from './event-grid';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSession,
  ApiError,
  ApprovalStatus,
  ApproveEventRpcRequest,
  ContactInterface,
  EventDetailRpcRequest,
  EventInterface,
  MeetingInterface,
  NoteInterface,
  RelatedObjectInterface,
  SaveOrApproveEventRpcRequest_Operation,
  SaveOrApproveEventRpcResponse,
} from '../../core/models';

interface MeetingRow {
  id?: number;
  date: string;
  time: string;
  room: string;
  status: ApprovalStatus | 'None';
  past: boolean;
}
interface OwnerRow {
  course: string;
  section: string;
  type: string;
  date: string;
  time: string;
  location: string;
}
interface ContactRow {
  role: string;
  name: string;
  email: string;
  phone: string;
}

const DAY_MS = 5; // minutes per slot
const EVENT_TYPE_LABELS: { [k: string]: string } = {
  Class: 'Class Event',
  FinalExam: 'Final Examination Event',
  MidtermExam: 'Midterm Examination Event',
  Course: 'Course Related Event',
  Special: 'Special Event',
  Unavailabile: 'Not Available',
  Message: 'Message',
};

/**
 * Event Detail — port of gwt/client/events/EventDetail.java. Loaded by id from
 * the events browser during GWT/Angular coexistence. Route: event/:id with an
 * optional ?term=<sessionId|abbrev> selecting the academic session (event RPCs
 * are session-scoped — the sessionId gates the EventAction permission check).
 *
 * Loads EventInterface via EventDetailRpcRequest (requestEventDetails factory:
 * sessionId + eventId) and renders name/type, sponsor, contacts, enrollment,
 * the meetings as an app-event-grid AND a table (date/time/room/approval),
 * related objects, notes and conflicts. Approve/Reject/Cancel are wired through
 * ApproveEventRpcRequest -> SaveOrApproveEventRpcResponse, acting on the
 * meetings whose per-meeting permission flags allow the operation.
 *
 * Deferred (noted): the per-meeting selection UI + inquire, student-enrollment
 * table (EventEnrollmentsRpcRequest), CSV/PDF export, edit navigation, and the
 * show/hide-deleted-meetings toggle. emailConfirmation is sent as false.
 */
@Component({
  selector: 'app-event-detail',
  imports: [
    FormsModule,
    CardModule,
    TableModule,
    ButtonModule,
    TagModule,
    TextareaModule,
    MessageModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './event-detail.html',
})
export class EventDetail {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private router = inject(Router);

  /** event id, from the route path (event/:id) */
  readonly id = input.required<string>();
  /** optional academic session (sessionId or abbreviation), from ?term= */
  readonly term = input<string | undefined>(undefined);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly acting = signal(false);
  protected readonly event = signal<EventInterface | null>(null);
  protected readonly sessions = signal<AcademicSession[]>([]);
  protected actionNote = '';

  private sessionsLoaded = false;
  private sessionId: number | null = null;

  constructor() {
    this.page.set('Event Detail');
    effect(() => {
      const id = this.id();
      const term = this.term();
      if (!id) return;
      untracked(() => this.start(Number(id), term));
    });
  }

  // ---- loading ------------------------------------------------------------

  private start(eventId: number, term?: string): void {
    if (this.sessionsLoaded) {
      this.resolveSession(term);
      this.loadDetail(eventId);
      return;
    }
    this.loading.set(true);
    this.rpc.execute<AcademicSession[]>('ListAcademicSessions', {}).subscribe({
      next: (list) => {
        this.sessions.set(list ?? []);
        this.sessionsLoaded = true;
        this.resolveSession(term);
        this.loadDetail(eventId);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private resolveSession(term?: string): void {
    const sessions = this.sessions();
    const t = term?.trim();
    if (t) {
      const byId = sessions.find((s) => String(s.uniqueId) === t);
      if (byId) {
        this.sessionId = byId.uniqueId ?? null;
        return;
      }
      const lower = t.toLowerCase();
      const byName = sessions.find(
        (s) => s.abbv?.toLowerCase() === lower || s.name?.toLowerCase() === lower,
      );
      if (byName) {
        this.sessionId = byName.uniqueId ?? null;
        return;
      }
      const n = Number(t);
      if (!Number.isNaN(n)) {
        this.sessionId = n;
        return;
      }
    }
    this.sessionId = (sessions.find((s) => s.selected) ?? sessions[0])?.uniqueId ?? null;
  }

  private loadDetail(eventId: number): void {
    this.loading.set(true);
    this.error.set(null);
    const request: EventDetailRpcRequest = { sessionId: this.sessionId ?? undefined, eventId };
    this.rpc.execute<EventInterface>('EventDetailRpcRequest', request).subscribe({
      next: (ev) => {
        this.event.set(ev ?? null);
        this.loading.set(false);
        if (ev?.eventName) this.page.set(ev.eventName);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.loading.set(false);
  }

  // ---- derived view models -------------------------------------------------

  protected readonly gridEvents = computed<EventInterface[]>(() => {
    const e = this.event();
    return e ? [e] : [];
  });

  protected readonly typeLabel = computed(() => {
    const t = this.event()?.eventType;
    return (t && EVENT_TYPE_LABELS[t]) || t || '';
  });

  protected readonly contacts = computed<ContactRow[]>(() => {
    const e = this.event();
    if (!e) return [];
    const rows: ContactRow[] = [];
    if (e.contact) rows.push(this.contactRow('Main Contact', e.contact));
    for (const c of e.additionalContacts ?? []) rows.push(this.contactRow('Additional', c));
    for (const c of e.instructors ?? []) rows.push(this.contactRow('Instructor', c));
    for (const c of e.coordinators ?? []) rows.push(this.contactRow('Coordinator', c));
    return rows;
  });

  protected readonly meetingRows = computed<MeetingRow[]>(() =>
    (this.event()?.meetings ?? []).map((m) => ({
      id: m.meetingId,
      date: m.meetingDate ?? '',
      time: this.meetingTime(m),
      room: m.location?.resourceName ?? m.location?.displayName ?? '',
      status: m.approvalStatus ?? 'None',
      past: !!m.past,
    })),
  );

  protected readonly owners = computed<OwnerRow[]>(() =>
    (this.event()?.relatedObjects ?? []).map((o) => this.ownerRow(o)),
  );

  protected readonly notes = computed<NoteInterface[]>(() => this.event()?.notes ?? []);

  protected readonly conflicts = computed(() =>
    (this.event()?.conflicts ?? []).map((c) => ({
      name: c.eventName ?? '',
      type: (c.eventType && EVENT_TYPE_LABELS[c.eventType]) || c.eventType || '',
      meetings: (c.meetings ?? []).length,
      dates: this.meetingSpan(c),
    })),
  );

  // ---- actions -------------------------------------------------------------

  private approvableMeetings(): MeetingInterface[] {
    return (this.event()?.meetings ?? []).filter((m) => m.canApprove);
  }
  private cancelableMeetings(): MeetingInterface[] {
    return (this.event()?.meetings ?? []).filter((m) => m.canCancel);
  }

  protected readonly canApprove = computed(() =>
    (this.event()?.meetings ?? []).some((m) => m.canApprove),
  );
  protected readonly canCancel = computed(() =>
    (this.event()?.meetings ?? []).some((m) => m.canCancel),
  );

  approve(): void {
    this.operate('APPROVE', this.approvableMeetings());
  }
  reject(): void {
    this.operate('REJECT', this.approvableMeetings());
  }
  cancel(): void {
    this.operate('CANCEL', this.cancelableMeetings());
  }

  private operate(
    operation: SaveOrApproveEventRpcRequest_Operation,
    meetings: MeetingInterface[],
  ): void {
    const ev = this.event();
    if (!ev || !meetings.length || this.acting()) return;
    this.acting.set(true);
    const request: ApproveEventRpcRequest = {
      operation,
      sessionId: this.sessionId ?? undefined,
      event: ev,
      meetings,
      message: this.actionNote.trim() || undefined,
      emailConfirmation: false,
    };
    this.rpc.execute<SaveOrApproveEventRpcResponse>('ApproveEventRpcRequest', request).subscribe({
      next: (res) => {
        this.acting.set(false);
        this.actionNote = '';
        for (const m of res.messages ?? []) {
          this.messages.add({
            severity: m.level === 'ERROR' ? 'error' : m.level === 'WARN' ? 'warn' : 'info',
            summary: ev.eventName,
            detail: m.message,
          });
        }
        this.messages.add({
          severity: 'success',
          summary: this.opLabel(operation),
          detail: ev.eventName,
        });
        if (res.event?.eventId) this.event.set(res.event);
        else this.loadDetail(Number(this.id()));
      },
      error: (e: ApiError) => {
        this.acting.set(false);
        this.messages.add({ severity: 'error', summary: this.opLabel(operation) + ' failed', detail: e.message });
      },
    });
  }

  private opLabel(op: SaveOrApproveEventRpcRequest_Operation): string {
    switch (op) {
      case 'APPROVE':
        return 'Approved';
      case 'REJECT':
        return 'Rejected';
      case 'CANCEL':
        return 'Cancelled';
      default:
        return op;
    }
  }

  back(): void {
    this.router.navigate(['/events']);
  }

  // ---- formatting helpers --------------------------------------------------

  statusSeverity(status: string): 'success' | 'danger' | 'warn' | 'secondary' | 'info' {
    switch (status) {
      case 'Approved':
        return 'success';
      case 'Rejected':
      case 'Deleted':
        return 'danger';
      case 'Cancelled':
        return 'warn';
      case 'Pending':
        return 'info';
      default:
        return 'secondary';
    }
  }

  private contactRow(role: string, c: ContactInterface): ContactRow {
    return {
      role,
      name: c.formattedName || [c.firstName, c.middleName, c.lastName].filter(Boolean).join(' '),
      email: c.email ?? '',
      phone: c.phone ?? c.responsibility ?? '',
    };
  }

  private ownerRow(o: RelatedObjectInterface): OwnerRow {
    return {
      course: (o.courseNames && o.courseNames.length ? o.courseNames.join(', ') : o.name) ?? '',
      section:
        o.externalIds && o.externalIds.length ? o.externalIds.join(', ') : o.sectionNumber ?? '',
      type: o.instruction || o.type || '',
      date: o.date ?? '',
      time: o.time ?? '',
      location: (o.locations ?? [])
        .map((l) => l.resourceName ?? l.displayName ?? '')
        .filter(Boolean)
        .join(', '),
    };
  }

  private meetingTime(m: MeetingInterface): string {
    if (m.startSlot == null || m.endSlot == null) return '';
    return `${this.slotTime(m.startSlot)}–${this.slotTime(m.endSlot)}`;
  }

  private slotTime(slot: number): string {
    const min = DAY_MS * slot;
    const h = Math.floor(min / 60) % 24;
    const m = min % 60;
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }

  private meetingSpan(e: EventInterface): string {
    const m = e.meetings ?? [];
    if (!m.length) return '';
    const first = m[0].meetingDate;
    const last = m[m.length - 1].meetingDate;
    return first === last ? first ?? '' : `${first} – ${last}`;
  }
}
