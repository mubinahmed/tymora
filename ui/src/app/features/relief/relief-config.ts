import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';
import { MultiSelectModule } from 'primeng/multiselect';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Relief Planning "Rules Configuration" (ReliefConfigRequest). The per-session policy
 * the allocation engine applies: weekly relief cap per teacher, academic-continuity
 * and non-teaching toggles, and the set of exempt staff.
 */
type Operation = 'LOAD' | 'SAVE';

interface StaffOption { uid: string; name: string; }
interface ReliefConfigRequest {
  operation: Operation;
  weeklyCap?: number | null;
  preferContinuity?: boolean;
  excludeNonTeaching?: boolean;
  sameDeptFirst?: boolean;
  exemptUids?: string[];
}
interface ReliefConfigResponse {
  weeklyCap?: number | null;
  preferContinuity?: boolean;
  excludeNonTeaching?: boolean;
  sameDeptFirst?: boolean;
  exemptUids?: string[];
  staff?: StaffOption[];
  canManage?: boolean;
}

@Component({
  selector: 'app-relief-config',
  imports: [
    FormsModule, ButtonModule, InputNumberModule, CheckboxModule, MultiSelectModule,
    CardModule, MessageModule, ProgressSpinnerModule,
  ],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="center"><p-progressSpinner strokeWidth="4" /></div>
      } @else if (error()) {
        <p-message severity="error" [text]="error()!" />
      } @else {
        <p-card header="Relief allocation rules">
          <div class="form">
            <div class="field">
              <label for="cap">Weekly relief cap per teacher</label>
              <p-inputNumber inputId="cap" [(ngModel)]="model.weeklyCap" [min]="1" [max]="40" [showButtons]="true" />
              <small>Maximum relief periods any one teacher is assigned per week.</small>
            </div>
            <div class="field-check">
              <p-checkbox inputId="cont" [(ngModel)]="model.preferContinuity" [binary]="true" />
              <label for="cont">Prefer academic continuity (same subject/department teacher)</label>
            </div>
            <div class="field-check">
              <p-checkbox inputId="dept" [(ngModel)]="model.sameDeptFirst" [binary]="true" />
              <label for="dept">Rank same-department teachers first</label>
            </div>
            <div class="field-check">
              <p-checkbox inputId="nt" [(ngModel)]="model.excludeNonTeaching" [binary]="true" />
              <label for="nt">Exclude non-teaching staff from the relief pool</label>
            </div>
            <div class="field">
              <label for="exempt">Exempt staff (never assigned relief)</label>
              <p-multiSelect inputId="exempt" [(ngModel)]="model.exemptUids" [options]="staff()"
                optionLabel="name" optionValue="uid" [filter]="true" filterBy="name"
                appendTo="body" placeholder="Select staff to exempt" styleClass="w-full" display="chip" />
              <small>Typically Key Personnel / HODs given a reduced or nil relief load.</small>
            </div>
          </div>
          <ng-template pTemplate="footer">
            @if (canManage()) {
              <p-button label="Save" icon="pi pi-check" (onClick)="save()" [loading]="saving()" />
            } @else {
              <p-message severity="info" text="You have read-only access to the relief configuration." />
            }
          </ng-template>
        </p-card>
      }
    </div>
  `,
  styles: [`
    .page { padding: 1rem; max-width: 46rem; }
    .form { display: flex; flex-direction: column; gap: 1.25rem; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field label { font-weight: 500; }
    .field small { opacity: .7; }
    .field-check { display: flex; align-items: center; gap: .6rem; }
    .center { display: flex; justify-content: center; padding: 3rem; }
  `],
})
export class ReliefConfig {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly staff = signal<StaffOption[]>([]);
  protected readonly canManage = signal(false);

  protected model: {
    weeklyCap: number | null;
    preferContinuity: boolean;
    excludeNonTeaching: boolean;
    sameDeptFirst: boolean;
    exemptUids: string[];
  } = { weeklyCap: 10, preferContinuity: true, excludeNonTeaching: true, sameDeptFirst: true, exemptUids: [] };

  constructor() {
    this.page.set('Relief Rules');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ReliefConfigResponse>('ReliefConfigRequest', { operation: 'LOAD' }).subscribe({
      next: (r) => {
        this.model = {
          weeklyCap: r.weeklyCap ?? 10,
          preferContinuity: !!r.preferContinuity,
          excludeNonTeaching: !!r.excludeNonTeaching,
          sameDeptFirst: !!r.sameDeptFirst,
          exemptUids: r.exemptUids ?? [],
        };
        this.staff.set(r.staff ?? []);
        this.canManage.set(!!r.canManage);
        this.loading.set(false);
      },
      error: (e: ApiError) => { this.error.set(e.message); this.loading.set(false); },
    });
  }

  save(): void {
    this.saving.set(true);
    const request: ReliefConfigRequest = { operation: 'SAVE', ...this.model };
    this.rpc.execute<ReliefConfigResponse>('ReliefConfigRequest', request).subscribe({
      next: () => { this.saving.set(false); this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Relief rules updated.' }); },
      error: (e: ApiError) => { this.saving.set(false); this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message }); },
    });
  }
}
