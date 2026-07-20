import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  UserEditData,
  UserInfo,
  UserListResponse,
  UserSaveRequest,
} from './user-edit.models';

/**
 * Users / Database Authentication (legacy userEdit.action) — list + add + edit + delete of the
 * database-authentication User rows, backed by UserList/UserEditLoad/UserSave/UserDeleteRequest.
 * A user has an external id (PUID), a user name and a password; the timetable-manager name and
 * (when enabled) API token are read-only. On edit the password is blank = keep existing.
 */
@Component({
  selector: 'app-user-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './user-edit.html',
})
export class UserEdit implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showTokens = signal(false);
  protected readonly users = signal<UserInfo[]>([]);

  // Edit form state (null = list mode)
  protected readonly form = signal<UserEditData | null>(null);
  protected password = '';

  ngOnInit(): void {
    this.page.set('Users');
    this.loadList();
  }

  private loadList(): void {
    this.loading.set(true);
    this.rpc.execute<UserListResponse>('UserListRequest', {}).subscribe({
      next: (d) => {
        this.showTokens.set(!!d.showTokens);
        this.users.set(d.users ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  add(): void {
    this.password = '';
    this.form.set({ newUser: true, showTokens: this.showTokens() });
  }

  edit(u: UserInfo): void {
    this.loading.set(true);
    this.rpc.execute<UserEditData>('UserEditLoadRequest', { externalId: u.externalId }).subscribe({
      next: (d) => {
        this.password = '';
        this.form.set(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message });
        this.loading.set(false);
      },
    });
  }

  backToList(): void {
    this.form.set(null);
    this.loadList();
  }

  save(): void {
    const f = this.form();
    if (!f) return;
    this.saving.set(true);
    const request: UserSaveRequest = {
      newUser: !!f.newUser,
      externalId: f.externalId,
      name: f.name,
      password: this.password,
    };
    this.rpc.execute<UserListResponse>('UserSaveRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.showTokens.set(!!d.showTokens);
        this.users.set(d.users ?? []);
        this.form.set(null);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'User saved.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  askDelete(): void {
    const f = this.form();
    if (!f?.externalId) return;
    this.confirm.confirm({
      header: 'Delete user',
      message: `Delete the user "${f.externalId}"?`,
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.doDelete(f.externalId!),
    });
  }

  private doDelete(externalId: string): void {
    this.saving.set(true);
    this.rpc.execute<UserListResponse>('UserDeleteRequest', { externalId }).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.showTokens.set(!!d.showTokens);
        this.users.set(d.users ?? []);
        this.form.set(null);
        this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'User deleted.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
      },
    });
  }
}
