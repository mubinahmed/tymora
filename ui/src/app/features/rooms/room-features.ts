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
import { ApiError, FeatureInterface, SearchRoomFeaturesRequest, UpdateRoomFeatureRequest } from '../../core/models';
import { RoomPropertyDialog } from './room-property-dialog';
import { RoomPropertiesService } from './room-properties.service';

@Component({
  selector: 'app-room-features',
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
  templateUrl: './room-features.html',
})
export class RoomFeatures implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);
  protected props = inject(RoomPropertiesService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly features = signal<FeatureInterface[]>([]);

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<FeatureInterface | null>(null);

  ngOnInit(): void {
    this.page.set('Room Features');
    this.props.ensureLoaded();
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: SearchRoomFeaturesRequest = { filter: { command: 'ENUMERATE', options: {} } };
    this.rpc.execute<FeatureInterface[]>('SearchRoomFeaturesRequest', request).subscribe({
      next: (list) => {
        this.features.set(list ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  scope(f: FeatureInterface): string {
    return f.department?.code ?? 'Global';
  }

  openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  openEdit(f: FeatureInterface): void {
    this.editing.set({ ...f });
    this.dialogVisible.set(true);
  }

  onSaved(): void {
    this.reload();
  }

  confirmDelete(f: FeatureInterface): void {
    this.confirm.confirm({
      header: 'Delete room feature',
      message: `Delete "${f.abbv} — ${f.label}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(f),
    });
  }

  private doDelete(f: FeatureInterface): void {
    const request: UpdateRoomFeatureRequest = { deleteFeatureId: f.id };
    this.rpc.execute<FeatureInterface>('UpdateRoomFeatureRequest', request).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Feature deleted', detail: f.abbv });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
