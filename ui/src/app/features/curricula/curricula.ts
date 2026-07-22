import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, CurriculumInterface, CurriculumFilterRpcRequest, MajorInterface } from '../../core/models';

const SERVICE = 'curricula.gwt';

/**
 * Curricula list — second classic-RemoteService screen (via /api/service).
 * Uses CurriculaService findCurricula / canAddCurriculum / deleteCurriculum.
 * The full curriculum editor (classifications + course projections grid) is
 * deferred; this covers browse + delete.
 */
@Component({
  selector: 'app-curricula',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './curricula.html',
})
export class Curricula implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly curricula = signal<CurriculumInterface[]>([]);

  // edit dialog (load-full / edit core / save-whole)
  protected readonly dialogVisible = signal(false);
  protected readonly saving = signal(false);
  private editing: CurriculumInterface | null = null;
  protected editAbbv = '';
  protected editName = '';

  ngOnInit(): void {
    this.page.set('Curricula');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const filter: CurriculumFilterRpcRequest = { command: 'ENUMERATE', options: {} };
    this.rpc.service<CurriculumInterface[]>(SERVICE, 'findCurricula', [filter]).subscribe({
      next: (list) => {
        this.curricula.set(list ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  majorLabel(majors?: MajorInterface[]): string {
    return (majors ?? []).map((m) => m.majorCode ?? m.majorName).filter(Boolean).join(', ');
  }

  openEdit(c: CurriculumInterface): void {
    // Load the full curriculum so classifications/courses are preserved on save.
    this.rpc.service<CurriculumInterface>(SERVICE, 'loadCurriculum', [c.id]).subscribe({
      next: (full) => {
        this.editing = full;
        this.editAbbv = full.abbv ?? '';
        this.editName = full.name ?? '';
        this.dialogVisible.set(true);
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  save(): void {
    if (!this.editing || !this.editAbbv.trim() || !this.editName.trim()) return;
    const merged: CurriculumInterface = { ...this.editing, abbv: this.editAbbv, name: this.editName };
    this.saving.set(true);
    this.rpc.service<number>(SERVICE, 'saveCurriculum', [merged]).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.messages.add({ severity: 'success', summary: 'Curriculum saved', detail: merged.abbv });
        this.reload();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(c: CurriculumInterface): void {
    this.confirm.confirm({
      header: 'Delete curriculum',
      message: `Delete "${c.abbv} — ${c.name}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(c),
    });
  }

  private doDelete(c: CurriculumInterface): void {
    this.rpc.service<boolean>(SERVICE, 'deleteCurriculum', [c.id]).subscribe({
      next: () => {
        this.messages.add({ severity: 'success', summary: 'Curriculum deleted', detail: c.abbv });
        this.reload();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
