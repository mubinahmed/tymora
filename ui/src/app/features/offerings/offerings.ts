import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  OfferingRow,
  SearchOfferingsRequest,
  SearchOfferingsResponse,
  SubjectAreaItem,
} from './offering-search.models';

/**
 * Instructional Offerings search — the Wave 4 entry point. Backed by a NEW
 * additive command bean (SearchOfferingsRequest) that wraps the legacy Struts
 * search's model access, so no existing backend logic changed. Rows link to the
 * command-pattern editor at /course-offering/:id. "Add Course" (Right.AddCourseOffering)
 * opens the same editor in create mode: /course-offering/new?subjectAreaId=..&courseNbr=..
 */
@Component({
  selector: 'app-offerings',
  imports: [
    FormsModule,
    RouterLink,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './offerings.html',
})
export class Offerings implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly subjectAreas = signal<SubjectAreaItem[]>([]);
  protected readonly offerings = signal<OfferingRow[]>([]);
  protected readonly searched = signal(false);
  /** Right.AddCourseOffering (from SearchOfferingsResponse.canAdd) */
  protected readonly canAdd = signal(false);

  protected subjectAreaId: number | null = null;
  protected courseNumber = '';

  ngOnInit(): void {
    this.page.set('Instructional Offerings');
    // Initial call with no subject area returns just the picker.
    this.rpc.execute<SearchOfferingsResponse>('SearchOfferingsRequest', {} as SearchOfferingsRequest).subscribe({
      next: (res) => {
        this.subjectAreas.set(res.subjectAreas ?? []);
        this.canAdd.set(!!res.canAdd);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  search(): void {
    if (this.subjectAreaId == null) return;
    this.searching.set(true);
    this.error.set(null);
    const request: SearchOfferingsRequest = {
      subjectAreaId: this.subjectAreaId,
      courseNumber: this.courseNumber || undefined,
    };
    this.rpc.execute<SearchOfferingsResponse>('SearchOfferingsRequest', request).subscribe({
      next: (res) => {
        this.offerings.set(res.offerings ?? []);
        this.canAdd.set(!!res.canAdd);
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.searching.set(false);
      },
    });
  }

  /** Open the course editor in create mode for the selected subject area (course number prefilled if typed). */
  addCourse(): void {
    if (this.subjectAreaId == null) return;
    const queryParams: Record<string, string | number> = { subjectAreaId: this.subjectAreaId };
    const nbr = this.courseNumber.trim();
    if (nbr) queryParams['courseNbr'] = nbr;
    this.router.navigate(['/course-offering', 'new'], { queryParams });
  }
}
