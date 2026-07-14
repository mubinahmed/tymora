import { Injectable, inject, signal } from '@angular/core';
import { RpcService } from '../../core/rpc.service';
import {
  FeatureTypeInterface,
  RoomInterface_AcademicSessionInterface,
  RoomInterface_DepartmentInterface,
  RoomPropertiesInterface,
  RoomPropertiesRequest,
} from '../../core/models';

/**
 * Loads and caches RoomPropertiesInterface (departments, feature types, future
 * sessions) once for the Rooms screens. Backing data for the group/feature
 * dialog's scope, type, and future-session pickers.
 */
@Injectable({ providedIn: 'root' })
export class RoomPropertiesService {
  private rpc = inject(RpcService);

  readonly departments = signal<RoomInterface_DepartmentInterface[]>([]);
  readonly featureTypes = signal<FeatureTypeInterface[]>([]);
  readonly futureSessions = signal<RoomInterface_AcademicSessionInterface[]>([]);

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
      },
      error: () => {
        // Non-fatal: pickers degrade to empty (global-only, no type, current session).
        this.loaded = false;
      },
    });
  }
}
