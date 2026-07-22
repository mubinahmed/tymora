import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { EventGrid } from './event-grid';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSession,
  ApiError,
  ContactInterface,
  EventInterface,
  EventLookupRpcRequest,
  ResourceInterface,
  ResourceLookupRpcRequest,
} from '../../core/models';

/**
 * Personal Timetable — the "personal" GWT page (EventResourceTimetable with
 * PageType.Personal). That page is locked to the PERSON resource type
 * ("type":"person","fixedType":"true") with an empty event filter, and resolves
 * a person through the Lookup dialog (mustHaveExternalId) before requesting
 * their schedule.
 *
 * We reproduce the functional core: search a person by name via
 * ResourceLookupRpcRequest (PERSON) — the picker only surfaces matches that
 * carry an external id — then look up that person's events via
 * EventLookupRpcRequest keyed by the resolved externalId. Read-only list +
 * weekly grid.
 *
 * Deferred vs. the GWT page: the modal person Lookup with directory search,
 * "All Sessions" iCal/export, add-to-calendar, and meeting-level detail/edit.
 */
@Component({
  selector: 'app-personal-timetable',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    SelectButtonModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './personal-timetable.html',
})
export class PersonalTimetable implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly matches = signal<ResourceInterface[]>([]);
  protected readonly events = signal<EventInterface[]>([]);
  protected readonly searched = signal(false);
  protected readonly person = signal<ResourceInterface | null>(null);

  protected personName = '';

  /**
   * Personal timetables are academic-session scoped: the EventAction permission
   * gate matches Right.Events against a Session qualifier, so every request MUST
   * carry the session id or the backend denies access.
   */
  protected readonly sessions = signal<AcademicSession[]>([]);
  protected sessionId: number | null = null;

  protected readonly view = signal<'list' | 'grid'>('list');
  protected readonly viewOptions = [
    { label: 'List', value: 'list', icon: 'pi pi-list' },
    { label: 'Grid', value: 'grid', icon: 'pi pi-calendar' },
  ];

  ngOnInit(): void {
    this.page.set('Personal Timetable');
    this.loadSessions();
  }

  /** Load selectable academic sessions and default to the current (selected) one. */
  private loadSessions(): void {
    this.loading.set(true);
    this.rpc.execute<AcademicSession[]>('ListAcademicSessions', {}).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.selected) ?? sessions[0])?.uniqueId ?? null;
        this.loading.set(false);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onSessionChange(): void {
    this.reset();
    // Re-run the current search against the newly selected session.
    if (this.personName.trim()) this.searchPeople();
  }

  private reset(): void {
    this.matches.set([]);
    this.events.set([]);
    this.person.set(null);
    this.searched.set(false);
    this.error.set(null);
  }

  /** Resolve the typed name to matching people (only those with an external id). */
  searchPeople(): void {
    if (!this.personName.trim()) return;
    this.reset();
    this.loading.set(true);
    const request: ResourceLookupRpcRequest = {
      resourceType: 'PERSON',
      name: this.personName.trim(),
      limit: 100,
      sessionId: this.sessionId ?? undefined,
    };
    this.rpc.execute<ResourceInterface[]>('ResourceLookupRpcRequest', request).subscribe({
      next: (list) => {
        const people = (list ?? []).filter((p) => !!p.externalId);
        this.matches.set(people);
        this.loading.set(false);
        if (people.length === 1) this.selectPerson(people[0]);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  /** Load the selected person's timetable (no event filter — all their events). */
  selectPerson(p: ResourceInterface): void {
    this.person.set(p);
    const request: EventLookupRpcRequest = {
      resourceType: 'PERSON',
      resourceExternalId: p.externalId,
      resourceId: p.resourceId,
      eventFilter: { command: 'ENUMERATE', options: {} },
      sessionId: this.sessionId ?? undefined,
    };
    this.loadingList.set(true);
    this.error.set(null);
    this.rpc.execute<EventInterface[]>('EventLookupRpcRequest', request).subscribe({
      next: (list) => {
        this.events.set(list ?? []);
        this.searched.set(true);
        this.loadingList.set(false);
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  personLabel(p: ResourceInterface): string {
    return [p.abbreviation, p.resourceName || p.displayName].filter(Boolean).join(' — ') || (p.externalId ?? '');
  }

  private fail(e: ApiError, list = false): void {
    this.error.set(e.message);
    (list ? this.loadingList : this.loading).set(false);
  }

  meetingSpan(e: EventInterface): string {
    const m = e.meetings ?? [];
    if (!m.length) return '—';
    const first = m[0].meetingDate;
    const last = m[m.length - 1].meetingDate;
    return first === last ? (first ?? '') : `${first} – ${last}`;
  }

  contactName(c?: ContactInterface): string {
    return c?.formattedName || [c?.firstName, c?.lastName].filter(Boolean).join(' ') || '';
  }
}
