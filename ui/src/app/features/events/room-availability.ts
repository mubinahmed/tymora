import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { EventGrid } from './event-grid';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSession,
  ApiError,
  Entity,
  EventInterface,
  EventLookupRpcRequest,
  FilterRpcResponse,
  MeetingInterface,
  RoomFilterRpcRequest,
  RPC_ROOM_FILTER,
} from '../../core/models';

interface RoomPick {
  id: number;
  label: string;
}
interface RoomResult {
  room: RoomPick;
  events: EventInterface[];
}

/**
 * Events → Room Availability — Angular port of the legacy GWT EventRoomAvailability.
 * Filters (academic session, time-of-day window, and a set of rooms) drive a
 * per-room representative-week timetable of the events occupying each room
 * (EventLookupRpcRequest per room, rendered with EventGrid). Rooms default to
 * the session's event rooms.
 *
 * Deferred vs. legacy: the session-dates calendar (specific-date grids rather
 * than the weekly pattern), inline Add Event / Event Detail dialogs, print, and
 * the room-filter chip syntax / sort menu.
 */
@Component({
  selector: 'app-room-availability',
  imports: [
    FormsModule,
    ButtonModule,
    SelectModule,
    MultiSelectModule,
    MessageModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './room-availability.html',
})
export class RoomAvailability implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly loadingRooms = signal(false);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly sessions = signal<AcademicSession[]>([]);
  protected sessionId: number | null = null;

  protected readonly rooms = signal<RoomPick[]>([]);
  protected selectedRoomIds: number[] = [];

  /** Optional time-of-day window (hour 0–24); filters displayed meetings. */
  protected readonly hours = Array.from({ length: 25 }, (_, h) => ({ label: `${h}:00`, value: h }));
  protected startHour: number | null = null;
  protected endHour: number | null = null;

  protected readonly results = signal<RoomResult[]>([]);

  /** Results with each event's meetings narrowed to the selected time window. */
  protected readonly filtered = computed<RoomResult[]>(() => {
    const from = this.startHour == null ? null : this.startHour * 12;
    const to = this.endHour == null ? null : this.endHour * 12;
    if (from == null && to == null) return this.results();
    return this.results().map((r) => ({
      room: r.room,
      events: r.events
        .map((e) => ({ ...e, meetings: (e.meetings ?? []).filter((m) => this.inWindow(m, from, to)) }))
        .filter((e) => (e.meetings ?? []).length > 0),
    }));
  });

  private inWindow(m: MeetingInterface, from: number | null, to: number | null): boolean {
    if (m.startSlot == null || m.endSlot == null) return false;
    if (from != null && m.endSlot <= from) return false;
    if (to != null && m.startSlot >= to) return false;
    return true;
  }

  ngOnInit(): void {
    this.page.set('Room Availability');
    this.loadSessions();
  }

  private loadSessions(): void {
    this.loading.set(true);
    this.rpc.execute<AcademicSession[]>('ListAcademicSessions', {}).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.selected) ?? sessions[0])?.uniqueId ?? null;
        this.loading.set(false);
        this.loadRooms();
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  onSessionChange(): void {
    this.selectedRoomIds = [];
    this.results.set([]);
    this.searched.set(false);
    this.loadRooms();
  }

  private loadRooms(): void {
    this.loadingRooms.set(true);
    this.error.set(null);
    // Default to the session's event rooms (matches the legacy "flag:Event" default).
    const request: RoomFilterRpcRequest = {
      command: 'ENUMERATE',
      options: { flag: ['Event'] },
      sessionId: this.sessionId ?? undefined,
    };
    this.rpc.execute<FilterRpcResponse>(RPC_ROOM_FILTER, request).subscribe({
      next: (res) => {
        this.rooms.set((res.entities?.['results'] ?? []).map((e: Entity) => ({ id: e.uniqueId!, label: e.name ?? '' })));
        this.loadingRooms.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingRooms.set(false);
      },
    });
  }

  clear(): void {
    this.selectedRoomIds = [];
    this.startHour = null;
    this.endHour = null;
    this.results.set([]);
    this.searched.set(false);
    this.error.set(null);
  }

  search(): void {
    if (!this.selectedRoomIds.length) {
      this.error.set('Select at least one room.');
      return;
    }
    this.searching.set(true);
    this.error.set(null);
    const byId = new Map(this.rooms().map((r) => [r.id, r]));
    const calls = this.selectedRoomIds.map((id) => {
      const request: EventLookupRpcRequest = {
        resourceType: 'ROOM',
        resourceId: id,
        eventFilter: { command: 'ENUMERATE', options: {} },
        roomFilter: { command: 'ENUMERATE', options: {} },
        sessionId: this.sessionId ?? undefined,
      };
      return this.rpc.execute<EventInterface[]>('EventLookupRpcRequest', request).pipe(
        map((events) => ({ room: byId.get(id)!, events: events ?? [] }) as RoomResult),
        catchError(() => of({ room: byId.get(id)!, events: [] } as RoomResult)),
      );
    });
    forkJoin(calls).subscribe({
      next: (rows) => {
        // Preserve the room selection order.
        this.results.set(rows);
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.searching.set(false);
      },
    });
  }
}
