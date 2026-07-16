import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, of, tap } from 'rxjs';
import { MenuItem } from 'primeng/api';
import { RpcService } from '../core/rpc.service';
import { MenuInterface } from '../core/models';

/**
 * Loads the backend-driven navigation (MenuRpcRequest -> MenuInterface[]) and
 * adapts it to PrimeNG MenuItem[]. This is the strangler routing switch:
 *   - a menu target already migrated to Angular  -> internal routerLink
 *   - anything else                              -> the existing legacy/GWT URL
 * As screens migrate, add their page key to MIGRATED and they light up in-app
 * with no backend change.
 */
@Injectable({ providedIn: 'root' })
export class MenuService {
  private rpc = inject(RpcService);
  private router = inject(Router);

  /** backend page key -> Angular route */
  private static readonly MIGRATED: Record<string, string> = {
    rooms: '/rooms',
    buildings: '/buildings',
    roomgroups: '/roomgroups',
    roomfeatures: '/roomfeatures',
    departments: '/departments',
    reservations: '/reservations',
    curricula: '/curricula',
    instructorattributes: '/instructorattributes',
    teachingRequests: '/teachingRequests',
    teachingAssignments: '/teachingAssignments',
    events: '/events',
    availability: '/room-availability',
    // Wave 1 GWT-backed screens
    traveltimes: '/travel-times',
    password: '/change-password',
    scripts: '/scripts',
    tasks: '/task-scheduler',
    hql: '/hql-reports',
    pointInTimeDataReports: '/point-in-time-data-reports',
    limitAndProjectionSnapshot: '/limit-and-projection-snapshot',
    assignmentHistory: '/assignment-history',
    solutionReports: '/solution-reports',
    solverlog: '/solver-log',
    listSolutions: '/saved-timetables',
    teachingAssignmentChanges: '/teaching-assignment-changes',
    exams: '/examinations',
    // Wave 2 larger GWT screens
    personal: '/personal-timetable',
    classes: '/lookup-classes',
    timetable: '/resource-timetable',
    sctreport: '/batch-sectioning-reports',
    onlinereport: '/online-sectioning-reports',
    batchsctdash: '/batch-sectioning-dashboard',
    onlinesctdash: '/online-sectioning-dashboard',
    instructorSurvey: '/instructor-survey',
    sectioning: '/scheduling-assistant',
    requests: '/course-requests',
    acrf: '/advisor-recommendations',
    solver: '/solver',
    assignedClasses: '/assignedClasses',
    notAssignedClasses: '/notAssignedClasses',
    solutionChanges: '/solutionChanges',
    timetableGrid: '/timetableGrid',
    cbs: '/cbs',
    publishedSolutions: '/publishedSolutions',
    // legacy Struts search now handled by an Angular screen + additive command bean
    'instructionalOfferingSearch.action': '/offerings',
    // Read-only admin listings via the new SimpleListBackend command bean.
    'sessionList.action': '/list/sessions',
    'itypeDescList.action': '/list/instructionalTypes',
    'distributionTypeList.action': '/list/distributionTypes',
    'datePatternEdit.action': '/pattern-edit',
    'timePatternEdit.action': '/pattern-edit',
    'instructorSearch.action': '/list/instructors',
    // Wave 5: search + create/edit screens (supersede the read-only listings where they exist)
    'classSearch.action': '/classes',
    'examList.action': '/examinations-list',
    'assignedExams.action': '/assigned-exams',
    'unassignedExams.action': '/unassigned-exams',
    'examAssignmentReport.action': '/exam-assignment-report',
    'examGrid.action': '/exam-grid',
    // Wave 7 (instructorDetail.action omitted: it is a by-id route, /instructor-detail/:id)
    'lastChanges.action': '/change-log',
    'manageSolvers.action': '/manage-solvers',
    'classAssignmentsReportSearch.action': '/class-assignments',
    'distributionPrefs.action': '/distribution-prefs-edit',
    'exactTimeEdit.action': '/exact-time',
    'examPeriodEdit.action': '/exam-periods-edit',
    // Wave 8: solver-proxy exams + projection rules
    'examChanges.action': '/exam-changes',
    'ecbs.action': '/exam-cbs',
    curprojrules: '/curriculum-projection-rules',
    'applicationConfig.action': '/application-config',
    'managerSettings.action': '/manager-settings',
    'deptStatusTypeEdit.action': '/status-types-edit',
    'timetableManagerList.action': '/managers-edit',
    'solverGroupEdit.action': '/solver-groups-edit',
    // Struts pages that already have an equivalent migrated Angular editor.
    'subjectList.action': '/admin/subjectArea',
    'departmentList.action': '/departments',
  };

  readonly items = signal<MenuItem[]>([]);
  private loaded = false;

  load(): void {
    if (this.loaded) return;
    this.loaded = true;
    this.rpc
      .execute<MenuInterface[]>('MenuRpcRequest')
      .pipe(
        catchError(() => of<MenuInterface[]>([])),
        tap((menu) => this.items.set(menu.map((m) => this.toItem(m)))),
      )
      .subscribe();
  }

  private toItem(m: MenuInterface): MenuItem {
    const children = (m.subMenus ?? []).map((s) => this.toItem(s));
    const item: MenuItem = { label: m.title || m.name };
    const nav = this.navFor(m);
    if (children.length) {
      // Dual-purpose node (own page + children, e.g. "Rooms"): PanelMenu parents
      // only toggle their submenu, so surface the node's own page as a leaf child
      // — otherwise that page (the Rooms list) would be unreachable from the menu.
      if (nav) children.unshift({ label: m.title || m.name, command: nav });
      item.items = children;
      return item;
    }
    if (nav) item.command = nav;
    return item;
  }

  /**
   * Navigation command for a menu node's own page: an in-app route when the page
   * is migrated, otherwise a same-tab hand-off to the legacy backend page.
   * Uses command (not routerLink/url) so PrimeNG renders no <a href/target>,
   * which avoids items opening in a new tab and keeps navigation under our control.
   */
  private navFor(m: MenuInterface): (() => void) | undefined {
    const route = this.routeFor(m);
    if (route) return () => this.router.navigateByUrl(route);
    if (m.page) {
      const url = this.legacyUrl(m);
      return () => window.location.assign(url);
    }
    return undefined;
  }

  /** Angular route for a migrated menu entry, or undefined for legacy pages. */
  private routeFor(m: MenuInterface): string | undefined {
    if (m.page === 'admin') {
      const type = m.parameters?.['type']?.[0];
      return type ? `/admin/${type}` : undefined;
    }
    return m.page ? MenuService.MIGRATED[m.page] : undefined;
  }

  /** Best-effort reconstruction of the legacy/GWT URL from the menu entry. */
  private legacyUrl(m: MenuInterface): string {
    let url = m.gWT ? `gwt.jsp?page=${encodeURIComponent(m.page!)}` : m.page!;
    const params = m.parameters ?? {};
    const qs = Object.entries(params)
      .flatMap(([k, vs]) => (vs ?? []).map((v) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`))
      .join('&');
    if (qs) url += (url.includes('?') ? '&' : '?') + qs;
    if (m.hash) url += `#${m.hash}`;
    return url;
  }
}
