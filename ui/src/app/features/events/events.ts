import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
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
  Entity,
  EventInterface,
  EventLookupRpcRequest,
  FilterRpcResponse,
  ResourceInterface,
  ResourceLookupRpcRequest,
  ResourceType,
  RoomFilterRpcRequest,
  RPC_ROOM_FILTER,
} from '../../core/models';

interface PickerItem {
  id: number;
  label: string;
}

/**
 * Events browser (command pattern). Supports resource types Room, Subject, and
 * Person: Room/Subject present a picker (rooms via RoomFilterRpcRequest, subjects
 * via ResourceLookupRpcRequest), Person is a name search (ResourceLookup PERSON
 * → resolves an externalId). Events come from EventLookupRpcRequest. Read-only
 * summary; the full calendar/timetable + meeting detail is deferred.
 */
@Component({
  selector: 'app-events',
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
  templateUrl: './events.html',
})
export class Events implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);

  protected readonly resourceTypes = [
    { label: 'Room', value: 'ROOM' as ResourceType },
    { label: 'Subject Area', value: 'SUBJECT' as ResourceType },
    { label: 'Department', value: 'DEPARTMENT' as ResourceType },
    { label: 'Curriculum', value: 'CURRICULUM' as ResourceType },
    { label: 'Course', value: 'COURSE' as ResourceType },
    { label: 'Group', value: 'GROUP' as ResourceType },
    { label: 'Person', value: 'PERSON' as ResourceType },
  ];
  protected resourceType: ResourceType = 'ROOM';

  get typeLabel(): string {
    return this.resourceTypes.find((t) => t.value === this.resourceType)?.label ?? 'resource';
  }

  protected readonly loading = signal(false);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly picks = signal<PickerItem[]>([]);
  protected readonly events = signal<EventInterface[]>([]);
  protected readonly searched = signal(false);

  protected resourceId: number | null = null;
  protected personName = '';

  /**
   * Events are academic-session scoped: the EventAction permission gate matches
   * Right.Events against a Session qualifier, so every request MUST carry the
   * session id or the backend denies access ("no matching role / academic session").
   */
  protected readonly sessions = signal<AcademicSession[]>([]);
  protected sessionId: number | null = null;

  protected readonly view = signal<'list' | 'grid'>('list');
  protected readonly viewOptions = [
    { label: 'List', value: 'list', icon: 'pi pi-list' },
    { label: 'Grid', value: 'grid', icon: 'pi pi-calendar' },
  ];

  ngOnInit(): void {
    this.page.set('Events');
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
        this.onTypeChange();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onSessionChange(): void {
    this.onTypeChange(); // reset picker + results for the newly selected session
  }

  get isPerson(): boolean {
    return this.resourceType === 'PERSON';
  }

  onTypeChange(): void {
    this.resourceId = null;
    this.picks.set([]);
    this.events.set([]);
    this.searched.set(false);
    this.error.set(null);
    if (this.isPerson) return; // person is a name search, no preloaded list

    this.loading.set(true);
    if (this.resourceType === 'ROOM') {
      const request: RoomFilterRpcRequest = { command: 'ENUMERATE', options: {}, sessionId: this.sessionId ?? undefined };
      this.rpc.execute<FilterRpcResponse>(RPC_ROOM_FILTER, request).subscribe({
        next: (res) => {
          this.picks.set((res.entities?.['results'] ?? []).map((e: Entity) => ({ id: e.uniqueId!, label: e.name ?? '' })));
          this.loading.set(false);
        },
        error: (e: ApiError) => this.fail(e),
      });
    } else {
      const request: ResourceLookupRpcRequest = {
        resourceType: this.resourceType,
        name: '',
        limit: 2000,
        sessionId: this.sessionId ?? undefined,
      };
      this.rpc.execute<ResourceInterface[]>('ResourceLookupRpcRequest', request).subscribe({
        next: (list) => {
          this.picks.set(
            (list ?? []).map((r) => ({ id: r.resourceId!, label: [r.abbreviation, r.resourceName].filter(Boolean).join(' — ') })),
          );
          this.loading.set(false);
        },
        error: (e: ApiError) => this.fail(e),
      });
    }
  }

  /** Room/Subject: look up events for the selected resource by id. */
  loadEvents(): void {
    if (this.resourceId == null) return;
    this.runLookup({
      resourceType: this.resourceType,
      resourceId: this.resourceId,
      eventFilter: { command: 'ENUMERATE', options: {} },
      roomFilter: { command: 'ENUMERATE', options: {} },
    });
  }

  /** Person: resolve the name to a resource, then look up that person's events. */
  searchPerson(): void {
    if (!this.personName.trim()) return;
    this.loadingList.set(true);
    this.error.set(null);
    const request: ResourceLookupRpcRequest = {
      resourceType: 'PERSON',
      name: this.personName.trim(),
      sessionId: this.sessionId ?? undefined,
    };
    this.rpc.execute<ResourceInterface[]>('ResourceLookupRpcRequest', request).subscribe({
      next: (list) => {
        const person = (list ?? [])[0];
        if (!person) {
          this.events.set([]);
          this.searched.set(true);
          this.loadingList.set(false);
          return;
        }
        this.runLookup({
          resourceType: 'PERSON',
          resourceExternalId: person.externalId,
          resourceId: person.resourceId,
          eventFilter: { command: 'ENUMERATE', options: {} },
        });
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  private runLookup(request: EventLookupRpcRequest): void {
    // The backend copies the outer sessionId into the nested event/room filters.
    request.sessionId = this.sessionId ?? undefined;
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

  /** Open the full event detail (carrying the selected session). */
  openEvent(e: EventInterface): void {
    if (e.eventId == null) return;
    this.router.navigate(['/event', e.eventId], { queryParams: { term: this.sessionId } });
  }

  /** Start a new event in the Add Event screen. */
  addEvent(): void {
    this.router.navigate(['/event-add'], { queryParams: { term: this.sessionId } });
  }
}
