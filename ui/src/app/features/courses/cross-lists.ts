import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { CrossCourse, CrossListsRequest, CrossListsResponse, CrossListsUpdateRequest } from './cross-lists.models';

/**
 * Cross Lists (legacy crossListsModify.action) — read + save of the controlling
 * course and per-course reservation limits for the existing cross-listed courses,
 * backed by CrossListsRequest / CrossListsUpdateRequest. Adding/removing courses
 * (which splits/merges offerings) stays on the legacy screen. Reached by offering id.
 */
@Component({
  selector: 'app-cross-lists',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    InputNumberModule,
    SelectModule,
    TableModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './cross-lists.html',
})
export class CrossLists implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<CrossListsResponse | null>(null);
  protected readonly courses = signal<CrossCourse[]>([]);
  protected readonly available = signal<CrossCourse[]>([]);
  protected readonly controllingId = signal<number | null>(null);
  protected toAdd: number | null = null;

  /** Reservations are editable unless the offering is unlimited or it is a single course. */
  protected readonly reservationEditable = computed(() => {
    const d = this.data();
    if (!d || d.unlimited) return false;
    return this.courses().length > 1 || !!d.singleCourseLimit;
  });

  ngOnInit(): void {
    this.page.set('Cross Lists');
    const oid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(oid)) {
      this.error.set('No instructional offering was specified.');
      this.loading.set(false);
      return;
    }
    const request: CrossListsRequest = { offeringId: oid };
    this.rpc.execute<CrossListsResponse>('CrossListsRequest', request).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private apply(d: CrossListsResponse): void {
    this.data.set(d);
    if (d.offeringName) this.page.set('Cross Lists — ' + d.offeringName);
    // Work on a copy so edits don't mutate the response signal in place.
    this.courses.set((d.courses ?? []).map((c) => ({ ...c })));
    this.available.set((d.availableCourses ?? []).map((c) => ({ ...c })));
    this.controllingId.set(d.controllingCourseId ?? null);
    this.toAdd = null;
    this.loading.set(false);
  }

  makeControlling(c: CrossCourse): void {
    this.controllingId.set(c.courseId ?? null);
  }

  addCourse(): void {
    if (this.toAdd == null) return;
    const pick = this.available().find((c) => c.courseId === this.toAdd);
    if (!pick) return;
    this.courses.set([...this.courses(), { ...pick, controlling: false, reservation: null, canDelete: true }]);
    this.available.set(this.available().filter((c) => c.courseId !== this.toAdd));
    this.toAdd = null;
  }

  removeCourse(c: CrossCourse): void {
    if (c.courseId === this.controllingId()) {
      this.messages.add({ severity: 'warn', summary: 'Controlling course', detail: 'Make another course controlling before removing this one.' });
      return;
    }
    if (this.courses().length <= 1) return;
    this.courses.set(this.courses().filter((x) => x.courseId !== c.courseId));
    // Offer it back in the add list.
    this.available.set([...this.available(), { courseId: c.courseId, courseName: c.courseName, title: c.title }]);
  }

  save(): void {
    const d = this.data();
    if (!d) return;
    if (this.controllingId() == null) {
      this.messages.add({ severity: 'warn', summary: 'Controlling course', detail: 'Select a controlling course.' });
      return;
    }
    this.saving.set(true);
    const request: CrossListsUpdateRequest = {
      offeringId: d.offeringId,
      controllingCourseId: this.controllingId()!,
      courses: this.courses().map((c) => ({ courseId: c.courseId, reservation: c.reservation ?? null })),
    };
    this.rpc.execute<CrossListsResponse>('CrossListsUpdateRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.apply(res);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Cross lists updated.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
