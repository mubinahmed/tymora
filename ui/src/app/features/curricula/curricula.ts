import { Component, OnInit, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
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
    TableModule,
    ButtonModule,
    InputTextModule,
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
