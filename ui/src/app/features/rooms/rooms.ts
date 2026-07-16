import { Component, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError, RoomDetailInterface } from '../../core/models';
import { RoomsService } from './rooms.service';
import { RoomPropertiesService } from './room-properties.service';

/**
 * Rooms list — entry point for the Add/Edit Room screens. Full room detail comes
 * from RoomDetailsBackend (via RoomsService), so each row already carries what the
 * editor needs. Add/Edit navigate to the routed room editor; Delete uses
 * RoomUpdateRpcRequest DELETE.
 */
@Component({
  selector: 'app-rooms',
  imports: [
    TableModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './rooms.html',
})
export class Rooms {
  private rooms = inject(RoomsService);
  protected props = inject(RoomPropertiesService);
  private page = inject(PageService);
  private router = inject(Router);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly list = this.rooms.rooms;
  private loadedOnce = false;

  constructor() {
    this.page.set('Rooms');
    this.props.ensureLoaded();
    // The room filter is session-gated, so wait until RoomProperties yields the
    // current session id, then load once.
    effect(() => {
      const sid = this.props.sessionId();
      if (sid != null && !this.loadedOnce) {
        this.loadedOnce = true;
        this.reload();
      }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rooms.list(this.props.sessionId()).subscribe({
      next: () => this.loading.set(false),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  add(): void {
    this.router.navigate(['/rooms/new']);
  }

  edit(r: RoomDetailInterface): void {
    this.router.navigate(['/rooms', r.uniqueId]);
  }

  availability(r: RoomDetailInterface): void {
    this.router.navigate(['/room-sharing', r.uniqueId]);
  }

  eventAvailability(r: RoomDetailInterface): void {
    this.router.navigate(['/room-sharing', r.uniqueId], { queryParams: { events: 1 } });
  }

  confirmDelete(r: RoomDetailInterface): void {
    this.confirm.confirm({
      header: 'Delete room',
      message: `Delete "${r.name}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(r),
    });
  }

  private doDelete(r: RoomDetailInterface): void {
    this.rooms.remove(r.uniqueId!, this.props.sessionId()).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Room deleted', detail: r.name });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
