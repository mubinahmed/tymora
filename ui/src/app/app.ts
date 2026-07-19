import { Component, OnInit, computed, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { PanelMenuModule } from 'primeng/panelmenu';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { AuthService } from './core/auth.service';
import { PageService } from './core/page.service';
import { MenuService } from './layout/menu.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PanelMenuModule, ButtonModule, ToastModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private auth = inject(AuthService);
  private menu = inject(MenuService);
  private router = inject(Router);
  protected page = inject(PageService);

  protected readonly menuItems = this.menu.items;
  protected readonly user = this.auth.user;
  protected readonly session = this.auth.session;
  protected readonly version = this.auth.version;

  /** Show the app chrome only once signed in; login renders full-page. */
  protected readonly authenticated = computed(() => !!this.user()?.name);

  protected readonly userLabel = computed(() => {
    const u = this.user();
    if (!u?.name) return 'Not signed in';
    return u.role ? `${u.name} (${u.role})` : u.name;
  });

  /** Initials for the header avatar chip (e.g. "Manager, Exam" -> "ME"). */
  protected readonly initials = computed(() => {
    const name = this.user()?.name?.trim();
    if (!name) return '–';
    const parts = name.split(/[\s,]+/).filter(Boolean);
    const letters = (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
    return (letters || name[0]).toUpperCase();
  });

  /** Small uppercase context line under the wordmark: current screen or module. */
  protected readonly subtitle = computed(() => this.page.title() || 'Timetabling');

  ngOnInit(): void {
    this.auth.ensureLoaded().subscribe(() => {
      if (this.auth.isAuthenticated()) this.menu.load();
    });
  }

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/signin']));
  }

  /**
   * Clicking the username mirrors the GWT header: masquerade (chameleon) when the
   * user may, otherwise switch role. Both are legacy backend pages (same tab).
   */
  userAction(): void {
    if (this.user()?.chameleon) {
      this.router.navigate(['/chameleon']); // modern in-app masquerade screen
    } else {
      window.location.assign('/selectPrimaryRole.action?list=Y'); // legacy role picker
    }
  }
}
