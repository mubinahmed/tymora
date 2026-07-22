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
import { ApiError, BuildingInterface, BuildingsDataResponse, UpdateBuildingRequest } from '../../core/models';
import { BuildingDialog } from './building-dialog';

@Component({
  selector: 'app-buildings',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
    BuildingDialog,
  ],
  providers: [ConfirmationService],
  templateUrl: './buildings.html',
})
export class Buildings implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly buildings = signal<BuildingInterface[]>([]);
  protected readonly canAdd = signal(false);

  protected readonly dialogVisible = signal(false);
  protected readonly editing = signal<BuildingInterface | null>(null);

  ngOnInit(): void {
    this.page.set('Buildings');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<BuildingsDataResponse>('GetBuildingsRequest', {}).subscribe({
      next: (res) => {
        this.buildings.set(res.buildings ?? []);
        this.canAdd.set(!!res.canAdd);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  openCreate(): void {
    this.editing.set(null);
    this.dialogVisible.set(true);
  }

  openEdit(b: BuildingInterface): void {
    this.editing.set({ ...b });
    this.dialogVisible.set(true);
  }

  onSaved(): void {
    this.reload();
  }

  confirmDelete(b: BuildingInterface): void {
    this.confirm.confirm({
      header: 'Delete building',
      message: `Delete "${b.abbreviation} — ${b.name}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(b),
    });
  }

  private doDelete(b: BuildingInterface): void {
    const request: UpdateBuildingRequest = { action: 'DELETE', building: { id: b.id } };
    this.rpc.execute<BuildingInterface>('UpdateBuildingRequest', request).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Building deleted', detail: b.abbreviation });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
