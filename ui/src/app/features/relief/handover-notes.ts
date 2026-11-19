import { HttpClient } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApiError } from '../../core/models';
import { RpcService } from '../../core/rpc.service';

/** Mirrors org.unitime.timetable.gwt.shared.StaffAbsenceInterface.HandoverNoteInfo. */
export interface HandoverNoteInfo {
  id?: number;
  textNote?: string;
  linkUrl?: string;
  linkLabel?: string;
  attachedName?: string;
  attachedSize?: number;
  createdByName?: string;
  timeStamp?: string;
  canDelete?: boolean;
}

/**
 * Relief handover: the absent teacher's shared instructions/link/attachment for
 * whoever covers their lessons (ITQ 6.6). Renders the notes already on an absence
 * and, when `canAdd`, a small form to add one more. Two-step upload mirrors the
 * room-picture flow: POST the file to /upload, then submit the note referencing
 * it; download goes through the same servlet via /upload?handover=<noteId>.
 */
@Component({
  selector: 'app-handover-notes',
  imports: [FormsModule, ButtonModule, InputTextModule, TextareaModule, MessageModule, ConfirmDialogModule],
  providers: [ConfirmationService],
  template: `
    <div class="handover">
      @if (notes.length) {
        <ul class="notes">
          @for (n of notes; track n.id) {
            <li>
              <div class="meta">
                <span class="author">{{ n.createdByName }}</span>
                <span class="time">{{ n.timeStamp }}</span>
                @if (n.canDelete) {
                  <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small" (onClick)="confirmRemove(n)" />
                }
              </div>
              @if (n.textNote) { <div class="text">{{ n.textNote }}</div> }
              @if (n.linkUrl) {
                <div class="link"><a [href]="n.linkUrl" target="_blank" rel="noopener">
                  <i class="pi pi-link"></i> {{ n.linkLabel || n.linkUrl }}
                </a></div>
              }
              @if (n.attachedName) {
                <div class="attachment"><a [href]="attachmentUrl(n)">
                  <i class="pi pi-paperclip"></i> {{ n.attachedName }} <span class="size">({{ formatSize(n.attachedSize) }})</span>
                </a></div>
              }
            </li>
          }
        </ul>
      } @else {
        <div class="empty">No handover instructions shared yet.</div>
      }

      @if (canAdd) {
        @if (!adding()) {
          <p-button label="Share instructions" icon="pi pi-plus" [text]="true" size="small" (onClick)="adding.set(true)" />
        } @else {
          <div class="form">
            <textarea pTextarea [(ngModel)]="draftText" rows="2" class="w-full" placeholder="Instructions for the relief teacher..."></textarea>
            <div class="row">
              <input pInputText [(ngModel)]="draftLinkUrl" placeholder="https://... (optional link)" class="flex-1" />
              <input pInputText [(ngModel)]="draftLinkLabel" placeholder="Link label (optional)" class="flex-1" />
            </div>
            <div class="row">
              <input type="file" #fileInput (change)="onFileSelected(fileInput)" />
              @if (draftFileName()) { <span class="picked">{{ draftFileName() }}</span> }
            </div>
            @if (error()) { <p-message severity="error" [text]="error()!" /> }
            <div class="actions">
              <p-button label="Cancel" severity="secondary" [text]="true" size="small" (onClick)="cancel()" />
              <p-button label="Save" icon="pi pi-check" size="small" [loading]="saving()" (onClick)="save()" />
            </div>
          </div>
        }
      }
      <p-confirmDialog />
    </div>
  `,
  styles: [`
    .handover { display: flex; flex-direction: column; gap: .5rem; }
    .notes { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .5rem; }
    .notes li { border: 1px solid var(--p-content-border-color, #ddd); border-radius: 6px; padding: .5rem .75rem; }
    .meta { display: flex; align-items: center; gap: .5rem; font-size: .75rem; opacity: .7; }
    .meta .author { font-weight: 600; }
    .meta .time { flex: 1; }
    .text { white-space: pre-wrap; margin-top: .25rem; }
    .link, .attachment { margin-top: .25rem; font-size: .9rem; }
    .attachment .size { opacity: .6; font-size: .8rem; }
    .empty { font-size: .85rem; opacity: .6; }
    .form { display: flex; flex-direction: column; gap: .5rem; border: 1px dashed var(--p-content-border-color, #ccc); border-radius: 6px; padding: .5rem .75rem; }
    .form .row { display: flex; gap: .5rem; align-items: center; }
    .form .flex-1 { flex: 1; }
    .form .picked { font-size: .8rem; opacity: .7; }
    .actions { display: flex; justify-content: flex-end; gap: .5rem; }
  `],
})
export class HandoverNotes {
  private rpc = inject(RpcService);
  private http = inject(HttpClient);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  /** Required when canAdd is true (targets ADD_NOTE/DELETE_NOTE); unused in read-only display. */
  @Input() absenceId?: number;
  @Input() notes: HandoverNoteInfo[] = [];
  @Input() canAdd = false;
  /** Whether the embedding screen is teacher self-service (My Leave) vs admin (Staff Absences). */
  @Input() mineOnly = false;
  @Output() notesChange = new EventEmitter<HandoverNoteInfo[]>();

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly draftFileName = signal<string | null>(null);
  protected draftText = '';
  protected draftLinkUrl = '';
  protected draftLinkLabel = '';
  private uploaded = false;

  attachmentUrl(n: HandoverNoteInfo): string {
    return `/upload?handover=${n.id}`;
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  onFileSelected(input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) return;
    this.error.set(null);
    const form = new FormData();
    form.append('file', file, file.name);
    this.http.post('/upload', form, { responseType: 'text' }).subscribe({
      next: (msg) => {
        if (/^(ERROR|No file)/i.test(msg.trim())) {
          this.error.set(msg);
          input.value = '';
          return;
        }
        this.uploaded = true;
        this.draftFileName.set(file.name);
      },
      error: (e: ApiError) => this.error.set(e.message),
    });
  }

  save(): void {
    if (!this.draftText.trim() && !this.draftLinkUrl.trim() && !this.uploaded) {
      this.error.set('Provide instructions, a link, or an attachment.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.rpc.execute<{ absences?: { id?: number; notes?: HandoverNoteInfo[] }[] }>('StaffAbsenceRequest', {
      operation: 'ADD_NOTE',
      mineOnly: this.mineOnly,
      absenceId: this.absenceId,
      useUploadedFile: this.uploaded,
      note: {
        textNote: this.draftText.trim() || undefined,
        linkUrl: this.draftLinkUrl.trim() || undefined,
        linkLabel: this.draftLinkLabel.trim() || undefined,
      },
    }).subscribe({
      next: (res) => {
        this.saving.set(false);
        const updated = (res.absences ?? []).find((a) => a.id === this.absenceId);
        this.notesChange.emit(updated?.notes ?? this.notes);
        this.cancel();
        this.messages.add({ severity: 'success', summary: 'Shared', detail: 'Handover instructions saved.' });
      },
      error: (e: ApiError) => { this.saving.set(false); this.error.set(e.message); },
    });
  }

  cancel(): void {
    this.adding.set(false);
    this.draftText = '';
    this.draftLinkUrl = '';
    this.draftLinkLabel = '';
    this.draftFileName.set(null);
    this.uploaded = false;
    this.error.set(null);
  }

  confirmRemove(n: HandoverNoteInfo): void {
    this.confirm.confirm({
      header: 'Remove handover note',
      message: 'Remove this shared instruction?',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.rpc.execute<{ absences?: { id?: number; notes?: HandoverNoteInfo[] }[] }>('StaffAbsenceRequest', {
        operation: 'DELETE_NOTE',
        mineOnly: this.mineOnly,
        absenceId: this.absenceId,
        noteId: n.id,
      }).subscribe({
        next: (res) => {
          const updated = (res.absences ?? []).find((a) => a.id === this.absenceId);
          this.notesChange.emit(updated?.notes ?? this.notes.filter((x) => x.id !== n.id));
          this.messages.add({ severity: 'success', summary: 'Removed' });
        },
        error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Remove failed', detail: e.message }),
      }),
    });
  }
}
