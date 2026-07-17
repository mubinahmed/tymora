import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { CheckboxModule } from 'primeng/checkbox';
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
  SessionMonth,
} from '../../core/models';

interface RoomPick {
  id: number;
  label: string;
}
interface RoomResult {
  room: RoomPick;
  events: EventInterface[];
}

/** A selectable session date, normalized to the backend day-of-year (session start year). */
interface SelectableDate {
  dayOfYear: number;
  iso: string; // yyyy-MM-dd
  label: string; // "Mon 01/05"
  dayOfWeek: number; // 0=Mon..6=Sun (UniTime)
  holiday: boolean;
  weekend: boolean;
  past: boolean;
}

/** One occupied slot within a room on a given date (for the date-specific table). */
interface DateSlotItem {
  eventName: string;
  eventType: string;
  time: string;
}
interface RoomDateRow {
  date: SelectableDate;
  items: DateSlotItem[];
}
interface RoomDateResult {
  room: RoomPick;
  rows: RoomDateRow[];
}

// SessionMonth.Flag ordinals (see EventInterface.SessionMonth.Flag) — bit = 1<<ordinal.
const F_START = 1 << 0;
const F_HOLIDAY = 1 << 3;
const F_BREAK = 1 << 4;
const F_DISABLED = 1 << 6;
const F_PAST = 1 << 7;
const F_WEEKEND = 1 << 8;

const MS_PER_DAY = 86400000;
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

/**
 * Events → Room Availability — Angular port of the legacy GWT EventRoomAvailability.
 * Filters (academic session, time-of-day window, and a set of rooms) drive a
 * per-room timetable of the events occupying each room (EventLookupRpcRequest per
 * room, rendered with EventGrid). Rooms default to the session's event rooms.
 *
 * Session-date calendar (ported from EventRoomAvailability's SessionDatesSelector):
 * the user may pick one or more specific session dates. When dates are picked, the
 * per-room meetings are narrowed to those day-of-year values (MeetingInterface.dayOfYear,
 * which the backend normalizes to the session start year — same normalization used
 * here) and a date-specific availability table is shown labeled with the actual
 * dates; the representative-week EventGrid is kept (also narrowed to the picked dates).
 * With no dates picked the grid shows the full representative week, as before.
 *
 * Deferred vs. legacy: inline Add Event / Event Detail dialogs, print, and the
 * room-filter chip syntax / sort menu.
 */
@Component({
  selector: 'app-room-availability',
  imports: [
    FormsModule,
    ButtonModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './room-availability.html',
  styles: [
    `
      .ra-dates-row { display: flex; align-items: flex-end; gap: 1rem; flex-wrap: wrap; }
      .ra-dates { flex: 1 1 260px; min-width: 220px; }
      .ra-past { display: flex; align-items: center; gap: 0.4rem; padding-bottom: 0.4rem; }
      .ra-date-caption { margin: 0 0 0.5rem; font-size: 0.85rem; color: var(--text-color-secondary, #6c757d); }
      table.ra-date-table { border-collapse: collapse; font-size: 0.85rem; width: 100%; margin-bottom: 0.75rem; }
      table.ra-date-table th, table.ra-date-table td { border: 1px solid var(--surface-border, #dee2e6); padding: 4px 8px; text-align: left; vertical-align: top; }
      table.ra-date-table th.date-c { white-space: nowrap; background: var(--surface-100, #f8f9fa); }
      table.ra-date-table tr.free td.status-c { color: #1b7f3b; }
      table.ra-date-table tr.busy td.status-c { color: #b3261e; }
      table.ra-date-table .ra-slot { display: inline-block; margin-right: 0.75rem; white-space: nowrap; }
      table.ra-date-table .ra-slot .ra-time { color: var(--text-color-secondary, #6c757d); }
      tr.wknd th.date-c { font-style: italic; }
      tr.hol th.date-c { color: #b26a00; }
    `,
  ],
})
export class RoomAvailability implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly loadingRooms = signal(false);
  protected readonly loadingDates = signal(false);
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

  // --- session-date calendar ---
  private sessionMonths: SessionMonth[] = [];
  private sessionYear = 1900;
  protected readonly allowPast = signal(false);
  protected readonly selectableDates = signal<SelectableDate[]>([]);
  /** Selected session dates (day-of-year values). Signal so filters recompute live. */
  protected readonly selectedDateDoys = signal<number[]>([]);
  /** dayOfYear -> date, for labeling meetings against the picked dates. */
  private readonly dateByDoy = computed(
    () => new Map(this.selectableDates().map((d) => [d.dayOfYear, d])),
  );

  protected readonly results = signal<RoomResult[]>([]);

  /**
   * Results with each event's meetings narrowed to the selected time window and,
   * when any session dates are picked, to those day-of-year values.
   */
  protected readonly filtered = computed<RoomResult[]>(() => {
    const from = this.startHour == null ? null : this.startHour * 12;
    const to = this.endHour == null ? null : this.endHour * 12;
    const doys = this.selectedDateDoys();
    const doySet = doys.length ? new Set(doys) : null;
    if (from == null && to == null && !doySet) return this.results();
    return this.results().map((r) => ({
      room: r.room,
      events: r.events
        .map((e) => ({
          ...e,
          meetings: (e.meetings ?? []).filter((m) => this.inWindow(m, from, to) && this.inDates(m, doySet)),
        }))
        .filter((e) => (e.meetings ?? []).length > 0),
    }));
  });

  /**
   * Per-room, per-selected-date availability rows (built from the narrowed
   * `filtered` events). Empty when no dates are picked — then the grid alone is shown.
   */
  protected readonly dateResults = computed<RoomDateResult[]>(() => {
    const doys = this.selectedDateDoys();
    if (!doys.length) return [];
    const byDoy = this.dateByDoy();
    const dates = doys
      .map((d) => byDoy.get(d))
      .filter((d): d is SelectableDate => !!d)
      .sort((a, b) => a.dayOfYear - b.dayOfYear);
    return this.filtered().map((r) => ({
      room: r.room,
      rows: dates.map((date) => {
        const items: DateSlotItem[] = [];
        for (const e of r.events) {
          for (const m of e.meetings ?? []) {
            if (m.dayOfYear !== date.dayOfYear) continue;
            items.push({
              eventName: e.eventName ?? '',
              eventType: e.eventType ?? 'Special',
              time: this.slotRange(m),
            });
          }
        }
        items.sort((a, b) => a.time.localeCompare(b.time));
        return { date, items };
      }),
    }));
  });

  /** dateResults keyed by room id, for per-room lookup in the template. */
  private readonly dateRowsByRoom = computed(
    () => new Map(this.dateResults().map((r) => [r.room.id, r.rows])),
  );

  dateRowsFor(roomId: number): RoomDateRow[] {
    return this.dateRowsByRoom().get(roomId) ?? [];
  }

  /** Whether any session dates are currently picked. */
  protected readonly hasDates = computed(() => this.selectedDateDoys().length > 0);

  /** Human-readable summary of the picked dates, for the caption. */
  protected readonly selectedDatesLabel = computed(() => {
    const byDoy = this.dateByDoy();
    return this.selectedDateDoys()
      .map((d) => byDoy.get(d))
      .filter((d): d is SelectableDate => !!d)
      .sort((a, b) => a.dayOfYear - b.dayOfYear)
      .map((d) => d.label)
      .join(', ');
  });

  private inWindow(m: MeetingInterface, from: number | null, to: number | null): boolean {
    if (m.startSlot == null || m.endSlot == null) return false;
    if (from != null && m.endSlot <= from) return false;
    if (to != null && m.startSlot >= to) return false;
    return true;
  }

  private inDates(m: MeetingInterface, doySet: Set<number> | null): boolean {
    if (!doySet) return true;
    return m.dayOfYear != null && doySet.has(m.dayOfYear);
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
        this.loadSessionDates();
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  onSessionChange(): void {
    this.selectedRoomIds = [];
    this.selectedDateDoys.set([]);
    this.sessionMonths = [];
    this.selectableDates.set([]);
    this.results.set([]);
    this.searched.set(false);
    this.loadRooms();
    this.loadSessionDates();
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

  // ------------------------------------------------------------------
  //  Session-date calendar (ported from EventAdd / SessionDatesSelector)
  // ------------------------------------------------------------------

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
      error: () => {
        // Non-fatal: without dates the page falls back to the representative-week grid.
        this.loadingDates.set(false);
      },
    });
  }

  onAllowPastChange(): void {
    this.rebuildSelectableDates();
  }

  /** iSessionYear = year of the SessionMonth whose days carry the START flag. */
  private computeSessionYear(months: SessionMonth[]): number {
    for (const m of months) {
      const days = m.days ?? [];
      if (days.some((d) => (d & F_START) !== 0)) return m.year ?? 1900;
    }
    return months[0]?.year ?? 1900;
  }

  private rebuildSelectableDates(): void {
    const allowPast = this.allowPast();
    const out: SelectableDate[] = [];
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
        out.push({
          dayOfYear: doy,
          iso,
          label: `${DAY_NAMES[dow]} ${this.pad(month0 + 1)}/${this.pad(day)}`,
          dayOfWeek: dow,
          holiday: (flags & (F_HOLIDAY | F_BREAK)) !== 0,
          weekend: (flags & F_WEEKEND) !== 0,
          past,
        });
      }
    }
    out.sort((a, b) => a.dayOfYear - b.dayOfYear);
    this.selectableDates.set(out);
    // drop selections that are no longer valid
    const valid = new Set(out.map((d) => d.dayOfYear));
    this.selectedDateDoys.update((sel) => sel.filter((d) => valid.has(d)));
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
    return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 366 : 365;
  }

  private pad(n: number): string {
    return n < 10 ? '0' + n : '' + n;
  }

  private slotTime(slot: number): string {
    const min = 5 * slot;
    const h = Math.floor(min / 60) % 24;
    const m = min % 60;
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }

  private slotRange(m: MeetingInterface): string {
    if (m.startSlot == null || m.endSlot == null) return '';
    return `${this.slotTime(m.startSlot)}–${this.slotTime(m.endSlot)}`;
  }

  clear(): void {
    this.selectedRoomIds = [];
    this.startHour = null;
    this.endHour = null;
    this.selectedDateDoys.set([]);
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
