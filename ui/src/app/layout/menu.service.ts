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
    solver: '/solver',
    assignedClasses: '/assignedClasses',
    notAssignedClasses: '/notAssignedClasses',
    solutionChanges: '/solutionChanges',
    timetableGrid: '/timetableGrid',
    cbs: '/cbs',
    publishedSolutions: '/publishedSolutions',
    // legacy Struts search now handled by an Angular screen + additive command bean
    'instructionalOfferingSearch.action': '/offerings',
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
    if (children.length) {
      item.items = children;
      return item;
    }
    // Use command (not routerLink/url) so PrimeNG renders no <a href/target> —
    // that avoids items opening in a new tab and keeps navigation under our control.
    const route = this.routeFor(m);
    if (route) {
      item.command = () => this.router.navigateByUrl(route);
    } else if (m.page) {
      // Coexistence: hand off to the existing backend page in the SAME tab.
      const url = this.legacyUrl(m);
      item.command = () => window.location.assign(url);
    }
    return item;
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
