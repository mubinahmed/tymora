import { Injectable, computed, inject, signal } from '@angular/core';
import { RpcService } from '../../core/rpc.service';
import {
  BuildingInterface,
  FeatureInterface,
  FeatureTypeInterface,
  GroupInterface,
  RoomInterface_AcademicSessionInterface,
  RoomInterface_DepartmentInterface,
  RoomPropertiesInterface,
  RoomPropertiesRequest,
  RoomTypeInterface,
} from '../../core/models';

/**
 * Loads and caches RoomPropertiesInterface (room types, buildings, departments,
 * feature types, features, groups, future sessions) once for the Rooms screens.
 * Backing data for the group/feature dialog and the Add/Edit Room form pickers.
 */
@Injectable({ providedIn: 'root' })
export class RoomPropertiesService {
  private rpc = inject(RpcService);

  readonly departments = signal<RoomInterface_DepartmentInterface[]>([]);
  readonly featureTypes = signal<FeatureTypeInterface[]>([]);
  readonly futureSessions = signal<RoomInterface_AcademicSessionInterface[]>([]);
  readonly roomTypes = signal<RoomTypeInterface[]>([]);
  readonly buildings = signal<BuildingInterface[]>([]);
  readonly features = signal<FeatureInterface[]>([]);
  readonly groups = signal<GroupInterface[]>([]);
  /** Current academic-session id — required by the events RoomFilter permission gate. */
  readonly sessionId = signal<number | null>(null);
  /** Whether the current user may add a (university) room — requires the session to have buildings. */
  readonly canAddRoom = signal(false);
  /** Whether the current user may add a non-university location (no building needed). */
  readonly canAddNonUniversity = signal(false);
  /** Legacy "Add Room" gate: either kind of location (RoomsPage enables on the OR). */
  readonly canAdd = computed(() => this.canAddRoom() || this.canAddNonUniversity());
  /** Room area unit (true => metric m², false => ft²) for the area field label. */
  readonly areaMetric = signal(false);

  private loaded = false;

  ensureLoaded(): void {
    if (this.loaded) return;
    this.loaded = true;
    const request: RoomPropertiesRequest = {};
    this.rpc.execute<RoomPropertiesInterface>('RoomPropertiesRequest', request).subscribe({
      next: (p) => {
        this.departments.set(p.departments ?? []);
        this.featureTypes.set(p.featureTypes ?? []);
        this.futureSessions.set(p.futureSessions ?? []);
        this.roomTypes.set(p.roomTypes ?? []);
        this.buildings.set(p.buildings ?? []);
        this.features.set(p.features ?? []);
        this.groups.set(p.groups ?? []);
        this.sessionId.set(p.session?.id ?? null);
        this.canAddRoom.set(!!p.session?.canAddRoom);
        this.canAddNonUniversity.set(!!p.session?.canAddNonUniversity);
        this.areaMetric.set(!!p.roomAreaMetricUnits);
      },
      error: () => {
        // Non-fatal: pickers degrade to empty (global-only, no type, current session).
        this.loaded = false;
      },
    });
  }
}
