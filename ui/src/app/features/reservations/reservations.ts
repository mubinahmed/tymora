import { Component, OnInit, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
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
    TableModule,
    ButtonModule,
    InputTextModule,
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
