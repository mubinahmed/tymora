import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  InstrOfferingConfigInterface,
  InstrOfferingConfigInterface_Reference as Reference,
  SubpartLine,
} from '../../core/generated/models.generated';

/**
 * Instructional Offering Config (legacy instructionalOfferingConfigEdit.action) — the
 * configuration structure editor, reusing the existing GWT InstrOfferingConfigBackend
 * unchanged (operation LOAD/SAVE/DELETE on `InstrOfferingConfigInterface`). Edits the
 * config's scheduling subparts (instructional type, number of classes, minutes/week,
 * limits, rooms, department) and the config limit / instructional method / duration type.
 * Keyed by InstrOfferingConfig id.
 */
@Component({
  selector: 'app-instructional-offering-config',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    CheckboxModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './instructional-offering-config.html',
})
export class InstructionalOfferingConfig implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private router = inject(Router);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<InstrOfferingConfigInterface | null>(null);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);
  protected readonly lines = computed<SubpartLine[]>(() => this.data()?.subpartLines ?? []);
  protected readonly itypeOptions = computed<Reference[]>(() => this.data()?.instructionalTypes ?? []);
  protected readonly deptOptions = computed<Reference[]>(() => this.data()?.departments ?? []);
  protected readonly imOptions = computed<Reference[]>(() => this.data()?.instructionalMethods ?? []);
  protected readonly dtOptions = computed<Reference[]>(() => this.data()?.durationTypes ?? []);

  ngOnInit(): void {
    this.page.set('Instructional Offering Configuration');
    const cid = this.id();
    if (cid == null || !Number.isFinite(Number(cid))) {
      this.error.set('No instructional offering configuration was specified.');
      this.loading.set(false);
      return;
    }
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.rpc
      .execute<InstrOfferingConfigInterface>('InstrOfferingConfigInterface', { operation: 'LOAD', configId: Number(this.id()) })
      .subscribe({
        next: (d) => {
          this.data.set(d);
          if (d.courseName) this.page.set(d.courseName + (d.configName ? ' [' + d.configName + ']' : ''));
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  addSubpart(): void {
    const d = this.data();
    if (!d) return;
    const template = (d.subpartLines ?? [])[0];
    const line: SubpartLine = {
      iType: this.itypeOptions()[0]?.id,
      numberOfClasses: 1,
      minutesPerWeek: template?.minutesPerWeek ?? 0,
      minClassLimit: d.limit ?? 0,
      maxClassLimit: d.limit ?? 0,
      numberOfRooms: 1,
      roomRatio: 1,
      departmentId: template?.departmentId,
      splitAttendance: false,
      editable: true,
      canDelete: true,
      indent: 0,
      label: '(new subpart)',
    };
    (d.subpartLines ?? (d.subpartLines = [])).push(line);
    this.data.set({ ...d });
  }

  deleteSubpart(line: SubpartLine): void {
    const d = this.data();
    if (!d?.subpartLines) return;
    const at = d.subpartLines.indexOf(line);
    if (at >= 0) d.subpartLines.splice(at, 1);
    this.data.set({ ...d });
  }

  save(): void {
    const d = this.data();
    if (!d) return;
    this.saving.set(true);
    const payload: InstrOfferingConfigInterface = { ...d, operation: 'SAVE' as InstrOfferingConfigInterface['operation'] };
    this.rpc.execute<InstrOfferingConfigInterface>('InstrOfferingConfigInterface', payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Configuration updated.' });
        this.load(); // reload — the SAVE response is minimal (ids only)
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  askDelete(): void {
    this.confirm.confirm({
      header: 'Delete configuration',
      message: 'Delete this entire configuration and all its subparts and classes?',
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.doDelete(),
    });
  }

  private doDelete(): void {
    const d = this.data();
    if (!d) return;
    this.saving.set(true);
    this.rpc
      .execute<InstrOfferingConfigInterface>('InstrOfferingConfigInterface', {
        operation: 'DELETE',
        configId: d.configId,
        offeringId: d.offeringId,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Configuration deleted.' });
          this.router.navigate(['/offering-detail', d.courseId ?? d.offeringId]);
        },
        error: (e: ApiError) => {
          this.saving.set(false);
          this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
        },
      });
  }
}
