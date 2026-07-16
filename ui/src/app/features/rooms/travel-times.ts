import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  Entity,
  FilterRpcResponse,
  Room,
  RoomFilterRpcRequest,
  RPC_ROOM_FILTER,
  TravelTimeResponse,
  TravelTimesRequest,
} from '../../core/models';

interface PickerItem {
  id: number;
  label: string;
}

/**
 * Travel Times — a room-to-room travel-time (in minutes) matrix editor
 * (port of gwt/client/rooms/TravelTimes.java, backed by TravelTimesRequest ->
 * TravelTimesBackend).
 *
 * Flow: INIT resolves the current academic session; the user picks rooms from the
 * managed-room enumeration (RoomFilterRpcRequest ENUMERATE) and presses Show, which
 * sends a LOAD carrying those room ids. The backend returns a symmetric matrix of
 * travel times plus auto-computed distances (shown greyed as defaults when no explicit
 * travel time exists). Edit toggles the cells writable; Save persists a SAVE command;
 * Back reverts to the loaded values.
 *
 * Deferred vs. the GWT original: building-boundary shading, cell-to-cell arrow-key
 * navigation, and the RoomFilterBox free-text filter syntax (here rooms are chosen via
 * a multi-select). The 100-room backend cap still applies.
 */
@Component({
  selector: 'app-travel-times',
  imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    MultiSelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './travel-times.html',
})
export class TravelTimes implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly sessionName = signal<string | null>(null);
  private sessionId: number | null = null;

  protected readonly loadingRooms = signal(false);
  protected readonly loadingMatrix = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly roomOptions = signal<PickerItem[]>([]);
  protected selectedRoomIds: number[] = [];

  /** Rooms of the currently displayed matrix, in order. */
  protected readonly rooms = signal<Room[]>([]);
  protected readonly editable = signal(false);
  protected readonly shown = signal(false);

  /** Canonical-pair -> explicit travel time in minutes (the persisted values). */
  private values = new Map<string, number>();
  /** Canonical-pair -> auto-computed distance in minutes (display-only defaults). */
  private defaults = new Map<string, number>();
  /** Snapshot of `values` taken at load / last save, restored by Back. */
  private baseline = new Map<string, number>();

  protected readonly canShow = computed(() => this.selectedRoomIds.length >= 2);

  ngOnInit(): void {
    this.page.set('Travel Times');
    this.init();
  }

  private init(): void {
    const request: TravelTimesRequest = { command: 'INIT' };
    this.rpc.execute<TravelTimeResponse>('TravelTimesRequest', request).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId ?? null;
        this.sessionName.set(res.sessionName ?? null);
        this.loadRoomOptions();
      },
      error: (e: ApiError) => this.error.set(e.message),
    });
  }

  private loadRoomOptions(): void {
    this.loadingRooms.set(true);
    const request: RoomFilterRpcRequest = {
      command: 'ENUMERATE',
      options: {},
      sessionId: this.sessionId ?? undefined,
    };
    this.rpc.execute<FilterRpcResponse>(RPC_ROOM_FILTER, request).subscribe({
      next: (res) => {
        this.roomOptions.set(
          (res.entities?.['results'] ?? []).map((e: Entity) => ({ id: e.uniqueId!, label: e.name ?? '' })),
        );
        this.loadingRooms.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingRooms.set(false);
      },
    });
  }

  show(): void {
    if (!this.canShow()) return;
    this.error.set(null);
    this.editable.set(false);
    this.loadingMatrix.set(true);
    const request: TravelTimesRequest = {
      command: 'LOAD',
      rooms: this.selectedRoomIds.map((id) => ({ id })),
    };
    this.rpc.execute<TravelTimeResponse>('TravelTimesRequest', request).subscribe({
      next: (res) => {
        this.buildMatrix(res.rooms ?? []);
        this.loadingMatrix.set(false);
        this.shown.set(true);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingMatrix.set(false);
      },
    });
  }

  private buildMatrix(rooms: Room[]): void {
    this.values.clear();
    this.defaults.clear();
    for (const room of rooms) {
      const id = room.id!;
      for (const [otherKey, tt] of Object.entries(room.travelTimes ?? {})) {
        this.values.set(this.pairKey(id, Number(otherKey)), tt);
      }
      for (const [otherKey, dist] of Object.entries(room.distances ?? {})) {
        this.defaults.set(this.pairKey(id, Number(otherKey)), dist);
      }
    }
    this.baseline = new Map(this.values);
    this.rooms.set(rooms);
  }

  private pairKey(a: number, b: number): string {
    return a < b ? `${a}:${b}` : `${b}:${a}`;
  }

  /** Explicit value for a cell, or empty string if only a default (or diagonal) applies. */
  cellValue(a: Room, b: Room): string {
    if (a.id === b.id) return '';
    const v = this.values.get(this.pairKey(a.id!, b.id!));
    return v == null ? '' : String(v);
  }

  /** The greyed default (auto distance) shown when no explicit value is set. */
  cellDefault(a: Room, b: Room): string {
    if (a.id === b.id) return '';
    if (this.values.has(this.pairKey(a.id!, b.id!))) return '';
    const d = this.defaults.get(this.pairKey(a.id!, b.id!));
    return d == null ? '' : String(d);
  }

  onCellInput(a: Room, b: Room, raw: string): void {
    if (a.id === b.id) return;
    const key = this.pairKey(a.id!, b.id!);
    const trimmed = (raw ?? '').replace(/[^0-9]/g, '');
    if (trimmed === '') {
      this.values.delete(key);
    } else {
      this.values.set(key, Number(trimmed));
    }
  }

  isDiagonal(a: Room, b: Room): boolean {
    return a.id === b.id;
  }

  edit(): void {
    this.error.set(null);
    this.editable.set(true);
  }

  back(): void {
    this.error.set(null);
    this.values = new Map(this.baseline);
    this.editable.set(false);
    // re-emit rooms so the template re-renders cell values from the restored map
    this.rooms.set([...this.rooms()]);
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    const rooms: Room[] = this.rooms().map((r) => {
      const travelTimes: { [key: string]: number } = {};
      for (const other of this.rooms()) {
        if (other.id === r.id) continue;
        const v = this.values.get(this.pairKey(r.id!, other.id!));
        if (v != null) travelTimes[String(other.id)] = v;
      }
      return { id: r.id, name: r.name, building: r.building, travelTimes };
    });
    const request: TravelTimesRequest = { command: 'SAVE', rooms };
    this.rpc.execute<TravelTimeResponse>('TravelTimesRequest', request).subscribe({
      next: () => {
        this.baseline = new Map(this.values);
        this.editable.set(false);
        this.saving.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.saving.set(false);
      },
    });
  }
}
