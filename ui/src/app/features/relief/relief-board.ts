import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/**
 * Relief Planning oversight board (ReliefBoardRequest). Pick a date, GENERATE the
 * day's relief cover, then review each vacated lesson with its auto-assigned relief
 * teacher and — for editors — override it from the ranked list of free candidates.
 */
type Operation = 'LOAD' | 'GENERATE' | 'REASSIGN' | 'CLEAR';

interface CandidateInfo { uid: string; name: string; sameDept: boolean; weekLoad: number; }
interface LessonInfo {
  id: number;
  absentName: string;
  reasonLabel: string;
  className: string;
  timeText: string;
  roomName: string;
  reliefUid?: string | null;
  reliefName?: string | null;
  status: number;
  statusLabel: string;
  candidates: CandidateInfo[];
}
interface ReliefBoardRequest { operation: Operation; date?: string; lessonId?: number; reliefUid?: string; }
interface ReliefBoardResponse { date?: string; canManage?: boolean; generatedCount?: number; lessons?: LessonInfo[]; }

@Component({
  selector: 'app-relief-board',
  imports: [
    FormsModule, TableModule, ButtonModule, SelectModule, DatePickerModule,
    TagModule, MessageModule, ProgressSpinnerModule,
  ],
  template: `
    <div class="page">
      <div class="toolbar">
        <div class="left">
          <label for="rb-date">Date</label>
          <p-datepicker inputId="rb-date" [(ngModel)]="date" dataType="string" dateFormat="yy-mm-dd"
            [showIcon]="true" appendTo="body" placeholder="yyyy-mm-dd" (onSelect)="load()" />
          <p-button label="Refresh" icon="pi pi-refresh" severity="secondary" [text]="true"
            (onClick)="load()" [disabled]="!date()" />
        </div>
        @if (canManage()) {
          <p-button label="Generate relief" icon="pi pi-cog" (onClick)="generate()"
            [disabled]="!date() || busy()" [loading]="busy()" />
        }
      </div>

      @if (loading()) {
        <div class="center"><p-progressSpinner strokeWidth="4" /></div>
      } @else if (error()) {
        <p-message severity="error" [text]="error()!" />
      } @else if (!date()) {
        <p-message severity="info" text="Pick a date to view the relief board." />
      } @else {
        <div class="summary">
          <p-tag [value]="lessons().length + ' affected lesson(s)'" severity="info" />
          <p-tag [value]="unassignedCount() + ' unassigned'" [severity]="unassignedCount() ? 'warn' : 'success'" />
        </div>
        <p-table [value]="lessons()" styleClass="p-datatable-sm p-datatable-gridlines" [rowHover]="true">
          <ng-template pTemplate="header">
            <tr>
              <th>Absent teacher</th>
              <th>Reason</th>
              <th>Class</th>
              <th>Time</th>
              <th>Venue</th>
              <th style="width: 20rem;">Relief teacher</th>
              <th>Status</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-l>
            <tr>
              <td>{{ l.absentName }}</td>
              <td>{{ l.reasonLabel }}</td>
              <td>{{ l.className }}</td>
              <td>{{ l.timeText }}</td>
              <td>{{ l.roomName }}</td>
              <td>
                @if (canManage()) {
                  <p-select [options]="optionsFor(l)" [ngModel]="l.reliefUid ?? ''"
                    optionLabel="label" optionValue="uid" appendTo="body" styleClass="w-full"
                    (onChange)="reassign(l, $event.value)" [filter]="true" filterBy="label" />
                } @else {
                  {{ l.reliefName || '—' }}
                }
              </td>
              <td><p-tag [value]="l.statusLabel" [severity]="statusSeverity(l.status)" /></td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td colspan="7">No absences recorded for this date. Add an absence, then Generate.</td></tr>
          </ng-template>
        </p-table>
      }
    </div>
  `,
  styles: [`
    .page { padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }
    .toolbar { display: flex; justify-content: space-between; align-items: end; gap: 1rem; flex-wrap: wrap; }
    .toolbar .left { display: flex; align-items: end; gap: .75rem; }
    .toolbar label { display: block; font-size: .8rem; opacity: .8; margin-bottom: .25rem; }
    .summary { display: flex; gap: .5rem; }
    .center { display: flex; justify-content: center; padding: 3rem; }
  `],
})
export class ReliefBoard {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly date = signal<string>('');
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lessons = signal<LessonInfo[]>([]);
  protected readonly canManage = signal(false);

  protected readonly unassignedCount = computed(() => this.lessons().filter((l) => !l.reliefUid).length);

  constructor() {
    this.page.set('Relief Board');
  }

  /** Candidate options for a lesson's dropdown: an "unassigned" entry + ranked free teachers. */
  optionsFor(l: LessonInfo): { uid: string; label: string }[] {
    const opts = [{ uid: '', label: '— Unassigned —' }];
    for (const c of l.candidates)
      opts.push({ uid: c.uid, label: `${c.name}${c.sameDept ? ' (same dept)' : ''} · ${c.weekLoad} this wk` });
    return opts;
  }

  load(): void {
    if (!this.date()) return;
    this.loading.set(true);
    this.error.set(null);
    this.send({ operation: 'LOAD', date: this.date() }, () => this.loading.set(false), (e) => {
      this.error.set(e.message);
      this.loading.set(false);
    });
  }

  generate(): void {
    if (!this.date()) return;
    this.busy.set(true);
    this.send({ operation: 'GENERATE', date: this.date() }, (r) => {
      this.busy.set(false);
      this.messages.add({ severity: 'success', summary: 'Relief generated', detail: `${r.generatedCount ?? 0} lesson(s) processed.` });
    }, (e) => {
      this.busy.set(false);
      this.messages.add({ severity: 'error', summary: 'Generation failed', detail: e.message });
    });
  }

  reassign(l: LessonInfo, uid: string): void {
    const op: Operation = uid ? 'REASSIGN' : 'CLEAR';
    this.send({ operation: op, date: this.date(), lessonId: l.id, reliefUid: uid }, () => {
      this.messages.add({ severity: 'success', summary: 'Updated', detail: l.className });
    }, (e) => {
      this.messages.add({ severity: 'error', summary: 'Update failed', detail: e.message });
      this.load(); // reload to revert the optimistic select
    });
  }

  statusSeverity(status: number): 'success' | 'warn' | 'info' {
    return status === 0 ? 'warn' : status === 2 ? 'info' : 'success';
  }

  private send(request: ReliefBoardRequest, done: (r: ReliefBoardResponse) => void, fail: (e: ApiError) => void): void {
    this.rpc.execute<ReliefBoardResponse>('ReliefBoardRequest', request).subscribe({
      next: (res) => {
        this.lessons.set(res.lessons ?? []);
        this.canManage.set(!!res.canManage);
        done(res);
      },
      error: (e: ApiError) => fail(e),
    });
  }
}
