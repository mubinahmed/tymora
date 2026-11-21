import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

const RPC = 'StudentImportRequest';
const MAX_SIZE = 10 * 1024 * 1024;

interface StudentImportRequest {
  fileName: string;
  content: string;
  incremental: boolean;
}
interface StudentImportResponse {
  success?: boolean;
  message?: string;
  rootElement?: string;
  incremental?: boolean;
  sessionLabel?: string;
  studentsBefore?: number;
  studentsAfter?: number;
  enrollmentsBefore?: number;
  enrollmentsAfter?: number;
  warnings?: number;
  errors?: number;
  logTruncated?: boolean;
  log?: string[];
}

/**
 * Student Import: uploads a students / studentEnrollments Data Exchange XML
 * file and imports it synchronously into the current academic session through
 * the StudentImportRequest command bean (permission DataExchange). Shows the
 * resulting student / enrollment counts and the import log.
 */
@Component({
  selector: 'app-student-import',
  imports: [FormsModule, ButtonModule, CheckboxModule, MessageModule],
  templateUrl: './student-import.html',
  styles: [
    `
      .si-file { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; margin: 0.75rem 0; }
      .si-file input[type='file'] { display: none; }
      .si-file-name { font-family: monospace; }
      .si-muted { color: var(--ut-muted); }
      .si-option { display: flex; align-items: center; gap: 0.5rem; margin: 0.5rem 0 1rem; }
      .si-example summary { cursor: pointer; font-weight: 600; margin-top: 0.75rem; }
      .si-pre { background: var(--ut-surface); border: 1px solid var(--ut-border); border-radius: 6px;
        padding: 0.6rem 0.75rem; font-size: 0.8rem; overflow: auto; white-space: pre; }
      .si-log { max-height: 24rem; overflow-y: auto; white-space: pre-wrap; }
      .si-counts { border-collapse: collapse; margin: 0.75rem 0; }
      .si-counts th, .si-counts td { border-bottom: 1px solid var(--ut-border); padding: 0.3rem 0.9rem; text-align: right; }
      .si-counts th:first-child, .si-counts td:first-child { text-align: left; }
      .si-warn { color: #b26a00; }
      .si-err { color: #c62828; }
    `,
  ],
})
export class StudentImport implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly fileName = signal<string | null>(null);
  protected readonly fileSize = signal(0);
  protected readonly importing = signal(false);
  protected readonly result = signal<StudentImportResponse | null>(null);
  protected incremental = true;
  private content: string | null = null;

  protected readonly example = `<?xml version="1.0" encoding="UTF-8"?>
<studentEnrollments campus="MBIT" year="2026" term="Fall">
  <student externalId="S0001" firstName="Alex" lastName="Example" email="alex.example@example.edu">
    <class name="IT 1001 Lec 1"/>
    <class name="IT 1001 Lab 1"/>
  </student>
  <student externalId="S0002" firstName="Sam" lastName="Sample" email="sam.sample@example.edu">
    <class name="IT 1001 Lec 1"/>
  </student>
</studentEnrollments>`;

  ngOnInit(): void {
    this.page.set('Student Import');
  }

  protected pick(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    if (!f) return;
    if (f.size > MAX_SIZE) {
      this.messages.add({ severity: 'error', summary: 'Student Import', detail: 'The file is larger than 10 MB.' });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.content = String(reader.result ?? '');
      this.fileName.set(f.name);
      this.fileSize.set(f.size);
      this.result.set(null);
    };
    reader.onerror = () => {
      this.messages.add({ severity: 'error', summary: 'Student Import', detail: 'Unable to read the file.' });
    };
    reader.readAsText(f);
  }

  protected doImport(): void {
    const name = this.fileName();
    if (!name || !this.content) {
      this.messages.add({ severity: 'warn', summary: 'Student Import', detail: 'Choose an XML file first.' });
      return;
    }
    this.importing.set(true);
    this.result.set(null);
    const req: StudentImportRequest = { fileName: name, content: this.content, incremental: this.incremental };
    this.rpc.execute<StudentImportResponse>(RPC, req).subscribe({
      next: (res) => {
        this.importing.set(false);
        this.result.set(res);
        if (res.success) {
          this.messages.add({ severity: 'success', summary: 'Import finished', detail: res.message ?? name });
        } else {
          this.messages.add({ severity: 'error', summary: 'Import failed', detail: res.message ?? name });
        }
      },
      error: (e: ApiError) => {
        this.importing.set(false);
        this.messages.add({ severity: 'error', summary: 'Import failed', detail: e.message });
      },
    });
  }

  protected delta(after?: number, before?: number): string {
    const d = (after ?? 0) - (before ?? 0);
    return (d >= 0 ? '+' : '') + d;
  }

  protected lineClass(line: string): string {
    if (line.startsWith('WARN')) return 'si-warn';
    if (line.startsWith('ERROR') || line.startsWith('FATAL')) return 'si-err';
    return '';
  }

  protected sizeLabel(): string {
    const s = this.fileSize();
    return s >= 1024 * 1024 ? (s / 1024 / 1024).toFixed(1) + ' MB' : Math.max(1, Math.round(s / 1024)) + ' KB';
  }
}
