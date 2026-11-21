import { Component, OnInit, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { FormsModule } from '@angular/forms';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { MyExamsResponse } from './seating.models';

/**
 * My Examinations: a student's published examination seats (date, time, venue, seat, extra time), and an
 * invigilator's published duties with their availability per examination period, which they can set for
 * future periods. Backed by MyExamsRequest.
 */
@Component({
  selector: 'app-my-exams',
  imports: [FormsModule, ButtonModule, TableModule, TagModule, MessageModule, SelectModule, ProgressSpinnerModule],
  template: `
    @if (loading()) {
      <div class="center"><p-progressSpinner strokeWidth="4" /></div>
    } @else if (error()) {
      <p-message severity="error" [text]="error()!" />
    } @else if (data(); as d) {
      <div class="me-page">
        @if (d.message) { <div class="empty-state">{{ d.message }}</div> }
        @if (d.student) {
          <section class="me-section">
            <h2><i class="pi pi-th-large"></i> My examination seats</h2>
            @if (!d.seatsPublished) {
              <div class="empty-state">Your examination seats have not been published yet.</div>
            } @else {
              <p-table [value]="d.seats ?? []" styleClass="p-datatable-sm p-datatable-striped">
                <ng-template pTemplate="header"><tr><th>Date</th><th>Time</th><th>Examination</th><th>Venue</th><th>Seat</th><th></th></tr></ng-template>
                <ng-template pTemplate="body" let-s>
                  <tr><td>{{ s.date }}</td><td>{{ s.time }}</td><td>{{ s.exam }}</td><td>{{ s.room }}</td><td><b class="seat">{{ s.seat }}</b></td>
                    <td>@if (s.extraTimePct) { <p-tag [value]="s.extraTimePct + '% extra time'" severity="info" /> }
                      @if (clash(s.periodId)) { <p-tag value="Clash: contact the examination office" severity="danger" /> }</td></tr>
                </ng-template>
              </p-table>
            }
          </section>
        }
        @if (d.invigilator) {
          <section class="me-section">
            <h2><i class="pi pi-users"></i> My invigilation duties</h2>
            @if (!d.dutiesPublished) {
              <div class="empty-state">Invigilation duties have not been published yet.</div>
            } @else {
              <p-table [value]="d.duties ?? []" styleClass="p-datatable-sm p-datatable-striped">
                <ng-template pTemplate="header"><tr><th>Date</th><th>Time</th><th>Venue</th><th>Duty</th></tr></ng-template>
                <ng-template pTemplate="body" let-x>
                  <tr><td>{{ x.date }}</td><td>{{ x.time }}</td><td>{{ x.room }}</td><td>{{ x.dutyType === 'CHIEF' ? 'Chief invigilator' : x.dutyType === 'RESERVE' ? 'Reserve' : 'Invigilator' }}</td></tr>
                </ng-template>
                <ng-template pTemplate="emptymessage"><tr><td colspan="4">You have no duties.</td></tr></ng-template>
              </p-table>
            }
            <h3>My availability</h3>
            <div class="muted">Tell the examination office when you cannot invigilate, or when you would prefer to. Approved leave is shown automatically.</div>
            <p-table [value]="d.periods ?? []" styleClass="p-datatable-sm">
              <ng-template pTemplate="header"><tr><th>Examination period</th><th style="width:16rem">Availability</th></tr></ng-template>
              <ng-template pTemplate="body" let-p>
                <tr><td>{{ p.name }}</td>
                  <td>
                    @if (availability(p.id) === 'ABSENT') { <p-tag value="On approved leave" severity="secondary" /> }
                    @else {
                      <p-select [options]="options" [ngModel]="availability(p.id) || 'AVAILABLE'" (ngModelChange)="setAvailability(p.id, $event)" optionLabel="label" optionValue="value" [style]="{ width: '100%' }" />
                    }
                  </td></tr>
              </ng-template>
            </p-table>
          </section>
        }
      </div>
    }
  `,
  styles: [`
    .me-page { display: grid; gap: 1.2rem; max-width: 60rem; }
    .me-section { display: grid; gap: 0.6rem; h2 { font-size: 1rem; margin: 0; display: flex; gap: 0.5rem; align-items: center; .pi { color: var(--ut-blue); } } h3 { font-size: 0.9rem; margin: 0.6rem 0 0; } }
    .muted { color: var(--ut-muted); font-size: 0.82rem; }
    .seat { font-size: 1.05rem; color: var(--ut-ink); }
  `],
})
export class MyExams implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<MyExamsResponse | null>(null);
  protected readonly options = [
    { label: 'Available', value: 'AVAILABLE' },
    { label: 'Prefer to invigilate', value: 'PREFERRED' },
    { label: 'Not available', value: 'UNAVAILABLE' },
  ];

  ngOnInit(): void {
    this.page.set('My Examinations');
    this.load({ operation: 'LOAD' });
  }

  private load(req: { operation: string; periodId?: number; availability?: string }): void {
    this.rpc.execute<MyExamsResponse>('MyExamsRequest', req).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
        if (req.operation === 'SET_AVAILABILITY') this.messages.add({ severity: 'success', summary: 'Availability saved', life: 3000 });
      },
      error: (e: ApiError) => {
        this.loading.set(false);
        if (!this.data()) this.error.set(e.message);
        else this.messages.add({ severity: 'error', summary: 'My Examinations', detail: e.message });
      },
    });
  }

  /** two or more papers in the same period (an examination timetable clash) */
  protected clash(periodId?: number): boolean {
    return (this.data()?.seats ?? []).filter((x) => x.periodId === periodId).length > 1;
  }

  protected availability(periodId?: number): string {
    return this.data()?.availability?.find((a) => a.id === periodId)?.name ?? '';
  }

  protected setAvailability(periodId: number | undefined, value: string): void {
    if (periodId == null) return;
    this.load({ operation: 'SET_AVAILABILITY', periodId, availability: value });
  }
}
