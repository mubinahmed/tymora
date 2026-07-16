import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Read-only detail of one DepartmentalInstructor, keyed by the :id route param
 * (command pattern -> InstructorDetailRequest / InstructorDetailResponse via the
 * new InstructorDetailBackend, gated by Right.InstructorDetail).
 *
 * Solver-dependent parts of the legacy instructorDetail.action (per-class
 * assigned time/room/conflicts, instructor unavailability, editable preferences)
 * are out of scope for this read-only projection; the backend returns only the
 * count of assigned classes plus the instructor's identity/department/contact.
 */
interface InstructorDetailResponse {
  id?: number;
  name?: string;
  email?: string;
  externalId?: string;
  accountName?: string;
  position?: string;
  academicTitle?: string;
  deptCode?: string;
  deptName?: string;
  note?: string;
  assignedClasses?: number;
}

@Component({
  selector: 'app-instructor-detail',
  imports: [CardModule, ButtonModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './instructor-detail.html',
  styles: [
    `
      .detail-page {
        padding: 1rem;
        max-width: 720px;
      }
      .detail-center {
        display: flex;
        justify-content: center;
        padding: 3rem 0;
      }
      .detail-grid {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: 0.5rem 1.5rem;
        margin: 0;
      }
      .detail-grid dt {
        font-weight: 600;
        color: var(--text-color-secondary, #6c757d);
      }
      .detail-grid dd {
        margin: 0;
        word-break: break-word;
      }
      .detail-actions {
        margin-top: 1.5rem;
      }
    `,
  ],
})
export class InstructorDetail implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly instructor = signal<InstructorDetailResponse | null>(null);

  ngOnInit(): void {
    this.page.set('Instructor Detail');
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam == null ? NaN : Number(idParam);
    if (!Number.isFinite(id)) {
      this.error.set('No instructor specified.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc
      .execute<InstructorDetailResponse>('InstructorDetailRequest', { instructorId: id })
      .subscribe({
        next: (r) => {
          this.instructor.set(r);
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  back(): void {
    this.router.navigate(['/list', 'instructors']);
  }
}
