import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  SimpleEditInterface,
  SimpleEditInterface_Field,
  SimpleEditInterface_PageName,
  SimpleEditInterface_Record,
} from '../../core/models';

// SimpleEditInterface.Flag ordinals -> bit masks (1 << ordinal).
const FLAG = { HIDDEN: 1 << 0, READ_ONLY: 1 << 1, NOT_EMPTY: 1 << 4 };

// These request names collide across interfaces, so address them by FQN
// (the facade always indexes beans by fully-qualified request class name).
const RPC = {
  pageName: 'org.unitime.timetable.gwt.shared.SimpleEditInterface$GetPageNameRpcRequest',
  load: 'org.unitime.timetable.gwt.shared.SimpleEditInterface$LoadDataRpcRequest',
  saveRecord: 'org.unitime.timetable.gwt.shared.SimpleEditInterface$SaveRecordRpcRequest',
  deleteRecord: 'org.unitime.timetable.gwt.shared.SimpleEditInterface$DeleteRecordRpcRequest',
};

interface Column {
  field: SimpleEditInterface_Field;
  index: number;
}

/**
 * Generic editor for the ~25 admin "simple edit" data types. One protocol
 * (LoadData / SaveRecord / DeleteRecord keyed by a `type` string) drives them
 * all; the table columns and the edit form are built dynamically from the
 * Field[] metadata the backend returns. Routed as /admin/:type.
 */
@Component({
  selector: 'app-simple-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    CheckboxModule,
    SelectModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './simple-edit.html',
})
export class SimpleEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  /** admin data type key, from the route (e.g. "area", "positionType"). */
  readonly type = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<SimpleEditInterface | null>(null);
  protected readonly title = signal('Administration');

  protected readonly dialogVisible = signal(false);
  protected readonly edit = signal<{ uniqueId?: number; values: string[]; deletable: boolean } | null>(null);
  protected readonly saving = signal(false);

  /** visible (non-hidden) fields with their index into the values array */
  protected readonly columns = computed<Column[]>(() =>
    (this.data()?.fields ?? [])
      .map((field, index) => ({ field, index }))
      .filter((c) => !this.hasFlag(c.field, FLAG.HIDDEN)),
  );
  protected readonly editable = computed(() => this.data()?.editable !== false);
  protected readonly addable = computed(() => this.data()?.addable !== false);

  constructor() {
    effect(() => {
      const type = this.type();
      if (type) this.load(type);
    });
  }

  private load(type: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<SimpleEditInterface_PageName>(RPC.pageName, { type }).subscribe({
      next: (pn) => {
        const t = pn.plural ?? pn.singular ?? 'Administration';
        this.title.set(t);
        this.page.set(t);
      },
      error: () => this.page.set('Administration'),
    });
    this.rpc.execute<SimpleEditInterface>(RPC.load, { type }).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  reload(): void {
    this.load(this.type());
  }

  // ---- flag / rendering helpers --------------------------------------------
  hasFlag(field: SimpleEditInterface_Field, mask: number): boolean {
    return ((field.flags ?? 0) & mask) === mask;
  }
  isReadonly(field: SimpleEditInterface_Field): boolean {
    return this.hasFlag(field, FLAG.READ_ONLY);
  }
  isRequired(field: SimpleEditInterface_Field): boolean {
    return this.hasFlag(field, FLAG.NOT_EMPTY);
  }

  /** table cell text for a record/column (maps list values to their label). */
  display(record: SimpleEditInterface_Record, col: Column): string {
    const raw = record.values?.[col.index] ?? '';
    if (col.field.type === 'list') {
      const opt = (col.field.values ?? []).find((v) => v.value === raw);
      return opt?.text ?? raw;
    }
    return raw;
  }
  isToggle(field: SimpleEditInterface_Field): boolean {
    return field.type === 'toggle';
  }
  isChecked(record: SimpleEditInterface_Record, col: Column): boolean {
    return (record.values?.[col.index] ?? '') === 'true';
  }

  // ---- edit dialog ----------------------------------------------------------
  openCreate(): void {
    const fields = this.data()?.fields ?? [];
    this.edit.set({ values: fields.map((f) => f.default ?? ''), deletable: true });
    this.dialogVisible.set(true);
  }
  openEdit(record: SimpleEditInterface_Record): void {
    this.edit.set({
      uniqueId: record.uniqueId,
      values: [...(record.values ?? [])],
      deletable: record.deletable !== false,
    });
    this.dialogVisible.set(true);
  }
  setValue(index: number, value: string): void {
    const e = this.edit();
    if (!e) return;
    const values = [...e.values];
    values[index] = value;
    this.edit.set({ ...e, values });
  }

  save(): void {
    const e = this.edit();
    if (!e) return;
    const record: SimpleEditInterface_Record = { uniqueId: e.uniqueId, values: e.values, deletable: e.deletable };
    this.saving.set(true);
    this.rpc.execute<SimpleEditInterface_Record>(RPC.saveRecord, { type: this.type(), record }).subscribe({
      next: () => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: e.uniqueId ? 'Saved' : 'Created' });
        this.dialogVisible.set(false);
        this.reload();
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: err.message });
      },
    });
  }

  confirmDelete(record: SimpleEditInterface_Record): void {
    this.confirm.confirm({
      header: 'Delete record',
      message: 'Delete this record? This cannot be undone.',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(record),
    });
  }
  private doDelete(record: SimpleEditInterface_Record): void {
    this.rpc
      .execute<SimpleEditInterface_Record>(RPC.deleteRecord, { type: this.type(), record: { uniqueId: record.uniqueId } })
      .subscribe({
        next: () => {
          this.messages.add({ severity: 'success', summary: 'Deleted' });
          this.reload();
        },
        error: (err: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: err.message }),
      });
  }
}
