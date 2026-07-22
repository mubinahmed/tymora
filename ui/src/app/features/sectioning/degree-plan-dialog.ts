import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import {
  DegreeCourseInterface,
  DegreeGroupInterface,
  DegreePlaceHolderInterface,
  DegreePlanInterface,
} from '../../core/models';

/** A flattened, selectable line rendered in the plan tree. */
interface PlanLine {
  id: string;
  depth: number;
  kind: 'group' | 'course' | 'placeholder';
  label: string;
  description?: string;
  choice?: boolean;
  critical?: boolean;
  course?: DegreeCourseInterface;
  selectable: boolean;
}

/** One picked course to feed into the request list (subject+course label). */
export interface PickedCourse {
  courseId?: number;
  courseName: string;
  courseTitle?: string;
  /** true when this course should be OR-grouped with the previous picked course (a "choice" group). */
  orWithPrevious: boolean;
}

/**
 * Degree Plans dialog (legacy DegreePlansDialog / DegreePlansTable). Lists the
 * student's degree plans; the chosen plan's groups / courses / placeholders are
 * shown as a checkbox tree. Applying returns the checked courses so the parent
 * can populate the course-request list. Courses inside a "choice" group are
 * OR-grouped into a single request (alternatives within a request).
 */
@Component({
  selector: 'app-degree-plan-dialog',
  imports: [FormsModule, DialogModule, ButtonModule, SelectModule, CheckboxModule, MessageModule],
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [style]="{ width: '46rem', maxWidth: '95vw' }"
      header="Degree Plans"
      [dismissableMask]="true"
    >
      @if (!plans().length) {
        <p-message severity="info" text="No degree plans are available for this student." />
      } @else {
        <div class="dp-head">
          <label>Plan</label>
          <p-select
            [options]="planOptions()"
            optionLabel="label"
            optionValue="value"
            [(ngModel)]="selectedPlanId"
            (onChange)="onPlanChange()"
            appendTo="body"
            styleClass="dp-select"
          />
        </div>
        <div class="dp-tree">
          @for (line of lines(); track line.id) {
            <div class="dp-row" [style.paddingLeft.px]="8 + line.depth * 18">
              @if (line.kind === 'course') {
                <p-checkbox [(ngModel)]="checked[line.id]" [binary]="true" />
              }
              <span
                class="dp-label"
                [class.dp-group]="line.kind === 'group'"
                [class.dp-ph]="line.kind === 'placeholder'"
              >
                {{ line.label }}
                @if (line.choice) { <span class="dp-tag">choose one</span> }
                @if (line.critical) { <span class="dp-tag dp-crit">critical</span> }
              </span>
              @if (line.description) { <span class="dp-desc">{{ line.description }}</span> }
            </div>
          }
        </div>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cancel" severity="secondary" (onClick)="visible.set(false)" />
        <p-button label="Add Selected Courses" icon="pi pi-check" [disabled]="!anyChecked()" (onClick)="apply()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [
    `
      .dp-head {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-bottom: 0.75rem;
      }
      .dp-head label { font-weight: 600; }
      .dp-select { min-width: 24rem; }
      .dp-tree {
        max-height: 55vh;
        overflow: auto;
        border: 1px solid var(--p-content-border-color, #ddd);
        border-radius: 6px;
        padding: 4px 0;
      }
      .dp-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 3px 8px;
      }
      .dp-group { font-weight: 600; }
      .dp-ph { font-style: italic; color: var(--p-text-muted-color, #888); }
      .dp-desc { color: var(--p-text-muted-color, #888); font-size: 0.85em; }
      .dp-tag {
        font-size: 0.7em;
        padding: 1px 6px;
        border-radius: 10px;
        background: var(--p-primary-100, #e0edff);
        color: var(--p-primary-700, #1d4ed8);
        margin-left: 6px;
      }
      .dp-crit { background: #fee2e2; color: #b91c1c; }
    `,
  ],
})
export class DegreePlanDialog {
  readonly visible = model<boolean>(false);
  readonly plans = input<DegreePlanInterface[]>([]);
  readonly picked = output<PickedCourse[]>();

  protected selectedPlanId: string | null = null;
  protected checked: Record<string, boolean> = {};
  private readonly version = signal(0);

  protected readonly planOptions = computed(() =>
    this.plans().map((p) => ({
      label: [p.name, p.degree, p.track].filter(Boolean).join(' — ') || (p.id ?? 'Plan'),
      value: p.id ?? p.name ?? '',
    })),
  );

  protected readonly currentPlan = computed<DegreePlanInterface | null>(() => {
    this.version();
    const plans = this.plans();
    if (!plans.length) return null;
    return plans.find((p) => (p.id ?? p.name) === this.selectedPlanId) ?? plans[0];
  });

  protected readonly lines = computed<PlanLine[]>(() => {
    const plan = this.currentPlan();
    if (!plan?.group) return [];
    const out: PlanLine[] = [];
    this.walkGroup(plan.group, 0, out, true);
    return out;
  });

  onPlanChange(): void {
    this.checked = {};
    this.version.update((v) => v + 1);
  }

  protected anyChecked(): boolean {
    return Object.values(this.checked).some(Boolean);
  }

  private walkGroup(group: DegreeGroupInterface, depth: number, out: PlanLine[], isRoot: boolean): void {
    if (!isRoot) {
      out.push({
        id: `g-${out.length}`,
        depth,
        kind: 'group',
        label: group.description || (group.choice ? 'Choose one of:' : 'Group'),
        choice: !!group.choice,
        critical: !!group.critical,
        selectable: false,
      });
    }
    const childDepth = isRoot ? depth : depth + 1;
    for (const c of group.courses ?? []) {
      out.push(this.courseLine(c, childDepth, !!group.choice));
    }
    for (const g of group.groups ?? []) {
      this.walkGroup(g, childDepth, out, false);
    }
    for (const ph of group.placeHolders ?? []) {
      out.push(this.placeholderLine(ph, childDepth));
    }
  }

  private courseLine(c: DegreeCourseInterface, depth: number, choice: boolean): PlanLine {
    const label = [c.subject, c.course].filter(Boolean).join(' ') || c.title || 'Course';
    const id = `c-${c.courseId ?? label}-${depth}-${label}`;
    return {
      id,
      depth,
      kind: 'course',
      label: [label, c.title].filter(Boolean).join(' — '),
      choice,
      critical: !!c.critical,
      course: c,
      selectable: true,
    };
  }

  private placeholderLine(ph: DegreePlaceHolderInterface, depth: number): PlanLine {
    return {
      id: `p-${depth}-${ph.name ?? ''}`,
      depth,
      kind: 'placeholder',
      label: ph.name || ph.type || 'Placeholder',
      selectable: false,
    };
  }

  apply(): void {
    const result: PickedCourse[] = [];
    // Group consecutive checked courses that belong to the same "choice" group
    // into OR-alternatives (mark orWithPrevious). Non-choice courses stand alone.
    const lines = this.lines();
    let prevChoiceGroupStart = -1;
    for (const line of lines) {
      if (line.kind !== 'course' || !this.checked[line.id] || !line.course) {
        if (line.kind === 'group') prevChoiceGroupStart = -1;
        continue;
      }
      const c = line.course;
      const name = [c.subject, c.course].filter(Boolean).join(' ') || c.title || '';
      const orWith = line.choice === true && prevChoiceGroupStart >= 0 && result.length > 0;
      result.push({
        courseId: c.courseId,
        courseName: name,
        courseTitle: c.title,
        orWithPrevious: orWith,
      });
      prevChoiceGroupStart = line.choice ? (prevChoiceGroupStart < 0 ? result.length - 1 : prevChoiceGroupStart) : -1;
    }
    if (result.length) this.picked.emit(result);
    this.visible.set(false);
  }
}
