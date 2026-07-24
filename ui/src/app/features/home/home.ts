import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { AuthService } from '../../core/auth.service';
import { PageService } from '../../core/page.service';
import { RoomDetailInterface } from '../../core/models';
import { RoomsService } from '../rooms/rooms.service';
import { RoomPropertiesService } from '../rooms/room-properties.service';

/** One bar in a horizontal bar chart: a category label + its count. */
interface Bar {
  label: string;
  value: number;
}

/** A quick-launch destination card. */
interface Launch {
  label: string;
  hint: string;
  icon: string;
  route: string;
}

/**
 * Landing dashboard. Combines the already-loaded session/user context (AuthService)
 * with a live facilities overview drawn from real facade data: RoomPropertiesService
 * (session id + reference catalogs) and RoomsService (the enumerated room list). KPI
 * tiles and two single-series bar charts (rooms by type, rooms by building) are all
 * computed from that data; the hero and quick-launch grid render regardless so the
 * page stays useful even when a role can't read rooms.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, DecimalPipe, ButtonModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private auth = inject(AuthService);
  private page = inject(PageService);
  private rooms = inject(RoomsService);
  protected props = inject(RoomPropertiesService);

  protected readonly user = this.auth.user;
  protected readonly session = this.auth.session;
  protected readonly version = this.auth.version;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  private loadedOnce = false;

  /** Time-of-day greeting (client clock; this is an in-app page, not an artifact). */
  protected readonly greeting = (() => {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
  })();

  protected readonly firstName = computed(() => {
    const n = this.user()?.name ?? '';
    // Names come "Last, First" — prefer the given name when present.
    const comma = n.indexOf(',');
    return (comma >= 0 ? n.slice(comma + 1) : n).trim() || 'there';
  });

  private readonly roomList = this.rooms.rooms;

  // ---- KPI tiles (real counts) ----
  protected readonly kpis = computed(() => {
    const rooms = this.roomList();
    const seats = rooms.reduce((s, r) => s + (r.capacity ?? 0), 0);
    return [
      { icon: 'pi-building', value: rooms.length, label: 'Rooms', route: '/rooms' },
      { icon: 'pi-map', value: this.props.buildings().length, label: 'Buildings', route: '/buildings' },
      { icon: 'pi-users', value: seats, label: 'Total seats', route: '/rooms' },
      { icon: 'pi-tags', value: this.props.roomTypes().length, label: 'Room types', route: '/rooms' },
      { icon: 'pi-sitemap', value: this.props.departments().length, label: 'Departments', route: '/departments' },
      { icon: 'pi-star', value: this.props.features().length, label: 'Room features', route: '/roomfeatures' },
    ];
  });

  // ---- Chart 1: rooms by type ----
  protected readonly byType = computed<Bar[]>(() => this.groupBy((r) => r.roomType?.label));

  // ---- Chart 2: rooms by building (top 8) ----
  protected readonly byBuilding = computed<Bar[]>(() =>
    this.groupBy((r) => r.building?.abbreviation ?? r.building?.name).slice(0, 8),
  );

  protected readonly hasRoomData = computed(() => this.roomList().length > 0);

  private groupBy(key: (r: RoomDetailInterface) => string | undefined | null): Bar[] {
    const counts = new Map<string, number>();
    for (const r of this.roomList()) {
      const k = (key(r) ?? '—').toString();
      counts.set(k, (counts.get(k) ?? 0) + 1);
    }
    return [...counts.entries()]
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
  }

  /** Bar width as a percentage of the largest value in its chart. */
  barPct(bars: Bar[], value: number): number {
    const max = bars.reduce((m, b) => Math.max(m, b.value), 0);
    return max > 0 ? Math.max(3, Math.round((value / max) * 100)) : 0;
  }

  protected readonly launches: Launch[] = [
    { label: 'Rooms', hint: 'Facilities & availability', icon: 'pi-building', route: '/rooms' },
    { label: 'Course Offerings', hint: 'Offerings, classes, preferences', icon: 'pi-book', route: '/offerings' },
    { label: 'Course Timetabling', hint: 'Run & review the solver', icon: 'pi-calendar', route: '/solver' },
    { label: 'Examinations', hint: 'Exams, rooms & reports', icon: 'pi-file-edit', route: '/examinations-list' },
    { label: 'Events', hint: 'Rooms, meetings & calendars', icon: 'pi-calendar-clock', route: '/events' },
    { label: 'Instructors', hint: 'Teaching assignments', icon: 'pi-user', route: '/teachingAssignments' },
  ];

  constructor() {
    // RoomProperties is session-gated; load rooms once the session id is known.
    this.props.ensureLoaded();
    effect(() => {
      const sid = this.props.sessionId();
      if (sid != null && !this.loadedOnce) {
        this.loadedOnce = true;
        this.load(sid);
      }
    });
  }

  ngOnInit(): void {
    this.page.set('Dashboard');
  }

  private load(sessionId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.rooms.list(sessionId).subscribe({
      next: () => this.loading.set(false),
      error: (e: { message?: string }) => {
        this.error.set(e?.message ?? 'Facilities data is unavailable for your role.');
        this.loading.set(false);
      },
    });
  }
}
