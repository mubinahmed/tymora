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
import { ApiError } from '../../core/models';
import { ChameleonSwitchRequest, ChameleonUserItem, GetChameleonUsersResponse } from './chameleon.models';

/**
 * Masquerade ("chameleon") — modern replacement for chameleon.action. Lists the
 * users an admin may impersonate (GetChameleonUsersRequest) and switches to one
 * (ChameleonSwitchRequest). After switching, the whole app is reloaded so the
 * new identity applies everywhere (menu, permissions, header).
 */
@Component({
  selector: 'app-chameleon',
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
  templateUrl: './chameleon.html',
})
export class Chameleon implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly users = signal<ChameleonUserItem[]>([]);
  protected readonly currentName = signal('');
  protected readonly masquerading = signal(false);

  ngOnInit(): void {
    this.page.set('Masquerade');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<GetChameleonUsersResponse>('GetChameleonUsersRequest', {}).subscribe({
      next: (r) => {
        this.users.set(r.users ?? []);
        this.currentName.set(r.currentName ?? '');
        this.masquerading.set(!!r.masquerading);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  masqueradeAs(u: ChameleonUserItem): void {
    this.confirm.confirm({
      header: 'Masquerade',
      message: `Sign in as ${u.name}? You'll act as this user until you stop.`,
      icon: 'pi pi-user',
      accept: () => this.switch({ puid: u.puid, name: u.name }),
    });
  }

  stopMasquerade(): void {
    this.switch({ puid: '' }); // empty puid -> backend restores the original user
  }

  private switch(request: ChameleonSwitchRequest): void {
    this.rpc.execute<unknown>('ChameleonSwitchRequest', request).subscribe({
      next: () => {
        // Reload the whole app so menu/permissions/header reflect the new identity.
        window.location.assign('/');
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Switch failed', detail: e.message }),
    });
  }
}
