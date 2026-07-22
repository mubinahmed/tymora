import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, ReservationInterface, ReservationFilterRpcRequest } from '../../core/models';

const SERVICE = 'reservation.gwt';

/**
 * Reservations list — the first screen on the classic RemoteService path (via
 * the /api/service facade), not the command pattern. Uses findReservations /
 * canAddReservation / delete on ReservationService.
 *
 * ReservationInterface is abstract/polymorphic (Course/Group/Individual/…); the
 * facade's Gson emits each item's runtime fields but no type discriminator, so
 * this list shows the common base fields. The multi-step create/edit wizard is
 * deferred (offering + type selection, students/curricula/groups).
 */
@Component({
  selector: 'app-reservations',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    DialogModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './reservations.html',
})
export class Reservations implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly reservations = signal<ReservationInterface[]>([]);

  // edit dialog (load-full / save-whole — enabled by the facade polymorphism adapter)
  protected readonly dialogVisible = signal(false);
  protected readonly saving = signal(false);
  private editing: ReservationInterface | null = null;
  protected editLimit: number | null = null;
  protected editExpiration = '';

  ngOnInit(): void {
    this.page.set('Reservations');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filter: ReservationFilterRpcRequest = { command: 'ENUMERATE', options: {} };
    this.rpc.service<ReservationInterface[]>(SERVICE, 'findReservations', [filter]).subscribe({
      next: (list) => {
        this.reservations.set(list ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  openEdit(r: ReservationInterface): void {
    // Load the full reservation (carries its @type discriminator) so save round-trips
    // the concrete subtype and preserves type-specific fields (students/curricula/…).
    this.rpc.service<ReservationInterface>(SERVICE, 'getReservation', [r.id]).subscribe({
      next: (full) => {
        this.editing = full;
        this.editLimit = full.limit ?? null;
        this.editExpiration = full.expirationDate ?? '';
        this.dialogVisible.set(true);
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  save(): void {
    if (!this.editing) return;
    const merged: ReservationInterface = {
      ...this.editing,
      limit: this.editLimit ?? undefined,
      expirationDate: this.editExpiration || undefined,
    };
    this.saving.set(true);
    this.rpc.service<number>(SERVICE, 'save', [merged]).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: 'Reservation saved' });
        this.reload();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(r: ReservationInterface): void {
    this.confirm.confirm({
      header: 'Delete reservation',
      message: `Delete this reservation for "${r.offering?.name ?? 'offering'}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(r),
    });
  }

  private doDelete(r: ReservationInterface): void {
    this.rpc.service<boolean>(SERVICE, 'delete', [r.id]).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Reservation deleted' });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
