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
import { ApiError, GroupInterface, SearchRoomGroupsRequest, UpdateRoomGroupRequest } from '../../core/models';
import { RoomPropertyDialog } from './room-property-dialog';
import { RoomPropertiesService } from './room-properties.service';

@Component({
  selector: 'app-room-groups',
  imports: [
    TableModule,
    ButtonModule,
    InputTextModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    RoomPropertyDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './room-groups.html',
})
export class RoomGroups implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);
  protected props = inject(RoomPropertiesService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly groups = signal<GroupInterface[]>([]);

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<GroupInterface | null>(null);

  ngOnInit(): void {
    this.page.set('Room Groups');
    this.props.ensureLoaded();
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: SearchRoomGroupsRequest = { filter: { command: 'ENUMERATE', options: {} } };
    this.rpc.execute<GroupInterface[]>('SearchRoomGroupsRequest', request).subscribe({
      next: (list) => {
        this.groups.set(list ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  scope(g: GroupInterface): string {
    return g.default ? 'Default' : (g.department?.code ?? 'Global');
  }

  openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  openEdit(g: GroupInterface): void {
    this.editing.set({ ...g });
    this.dialogVisible.set(true);
  }

  onSaved(): void {
    this.reload();
  }

  confirmDelete(g: GroupInterface): void {
    this.confirm.confirm({
      header: 'Delete room group',
      message: `Delete "${g.abbv} — ${g.label}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(g),
    });
  }

  private doDelete(g: GroupInterface): void {
    const request: UpdateRoomGroupRequest = { deleteGroupId: g.id };
    this.rpc.execute<GroupInterface>('UpdateRoomGroupRequest', request).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Group deleted', detail: g.abbv });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
