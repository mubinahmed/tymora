import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { RoomPropertiesService } from './room-properties.service';
import { RoomSharingMatrix } from './room-sharing-matrix';
import { ApiError, RoomSharingModel } from '../../core/models';

/**
 * Per-room availability editor — Angular port of the legacy GWT RoomSharingPage
 * (page `roomavailability`). Loads a single room's sharing model (or its event
 * availability when `?events=1`), edits it with the RoomSharingMatrix, and saves
 * via RoomSharingRequest. Reached per-room from the Rooms list.
 */
@Component({
  selector: 'app-room-sharing-edit',
  imports: [CardModule, ButtonModule, MessageModule, ProgressSpinnerModule, RoomSharingMatrix],
  templateUrl: './room-sharing-edit.html',
})
export class RoomSharingEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private props = inject(RoomPropertiesService);
  private messages = inject(MessageService);
  private router = inject(Router);

  /** Room id (route param) and the optional `events` query param (=1 => event availability). */
  readonly id = input.required<string>();
  readonly events = input<string>();

  protected readonly eventAvailability = computed(() => this.events() === '1');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly model = signal<RoomSharingModel | null>(null);

  protected readonly title = computed(() => {
    const base = this.eventAvailability() ? 'Edit Room Event Availability' : 'Edit Room Availability';
    const name = this.model()?.name;
    return name ? `${base} · ${name}` : base;
  });

  constructor() {
    this.props.ensureLoaded();
    effect(() => {
      const id = this.id();
      if (id) this.load(Number(id));
    });
  }

  private load(locationId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.model.set(null);
    this.page.set(this.eventAvailability() ? 'Edit Room Event Availability' : 'Edit Room Availability');
    this.rpc
      .execute<RoomSharingModel>('RoomSharingRequest', {
        operation: 'LOAD',
        sessionId: this.props.sessionId() ?? undefined,
        locationId,
        eventAvailability: this.eventAvailability(),
        includeRoomPreferences: !this.eventAvailability(),
      })
      .subscribe({
        next: (m) => {
          this.model.set(m);
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  save(): void {
    const model = this.model();
    if (!model) return;
    this.saving.set(true);
    this.error.set(null);
    this.rpc
      .execute<RoomSharingModel>('RoomSharingRequest', {
        operation: 'SAVE',
        sessionId: this.props.sessionId() ?? undefined,
        locationId: Number(this.id()),
        model,
        eventAvailability: this.eventAvailability(),
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.messages.add({ severity: 'success', summary: 'Availability saved', detail: model.name });
          this.router.navigate(['/rooms']);
        },
        error: (e: ApiError) => {
          this.saving.set(false);
          this.error.set(e.message);
          this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
        },
      });
  }

  back(): void {
    this.router.navigate(['/rooms']);
  }
}
