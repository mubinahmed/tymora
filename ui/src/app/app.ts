import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { PanelMenuModule } from 'primeng/panelmenu';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { AuthService } from './core/auth.service';
import { PageService } from './core/page.service';
import { ThemeService } from './core/theme.service';
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
  private theme = inject(ThemeService);
  protected page = inject(PageService);

  protected readonly isDark = this.theme.isDark;

  protected readonly menuItems = this.menu.items;
  protected readonly user = this.auth.user;
  protected readonly session = this.auth.session;
  protected readonly version = this.auth.version;

  /** Current router URL (tracked so full-page screens can suppress the shell). */
  private readonly url = signal(this.router.url);

  /**
   * The role/session picker takes over the whole page: no shell, no menu — the
   * menu must not be available until a session is selected. (Login is already
   * full-page because it renders while unauthenticated.)
   */
  private readonly onPicker = computed(() => this.url().split('?')[0].startsWith('/select-role'));

  /** Show the app chrome only once signed in AND not on the session picker. */
  protected readonly authenticated = computed(() => !!this.user()?.name && !this.onPicker());

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

  constructor() {
    // Track the active URL so the shell can be suppressed on full-page screens
    // (the session picker). Root App lives for the whole app, so no teardown.
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.url.set(e.urlAfterRedirects));

    // Load the menu as soon as the user is authenticated. Reacting to the user
    // signal (rather than only in ngOnInit) covers BOTH a cold load with an
    // existing session AND an in-app (SPA) login — after which the root App is
    // never re-created, so ngOnInit would not run again and the menu would stay
    // stuck on "Loading menu…". menu.load() is idempotent.
    effect(() => {
      if (this.auth.user()?.name) this.menu.load();
    });
  }

  ngOnInit(): void {
    // Kick off the user/session/version fetch; the effect above loads the menu
    // once the user resolves.
    this.auth.ensureLoaded().subscribe();
  }

  logout(): void {
    // End the session WITHOUT following Spring Security's 302 -> /login.action.
    // Angular's HttpClient (XHR) transparently follows that redirect, and in this
    // setup the followed request re-establishes a session, so the immediate
    // ensureLoaded() re-check still sees the signed-in user and bounces
    // /signin -> /home. redirect:'manual' stops the follow: the backend still
    // invalidates the server session and the browser still applies the JSESSIONID
    // deletion from the 302. We then hard-navigate to the Angular login so the
    // whole SPA (signals, the shareReplay'd auth cache) is torn down and re-checks
    // auth from a clean slate. URLs resolve against document.baseURI (the
    // <base href>), matching how HttpClient resolves the api/* calls.
    const base = document.baseURI;
    fetch(new URL('logout.action', base).href, { credentials: 'include', redirect: 'manual', cache: 'no-store' })
      .catch(() => {})
      .finally(() => window.location.assign(new URL('signin', base).href));
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  /**
   * Clicking the username mirrors the GWT header: masquerade (chameleon) when the
   * user may, otherwise switch role. Both are now in-app Angular screens.
   */
  userAction(): void {
    if (this.user()?.chameleon) {
      this.router.navigate(['/chameleon']); // modern in-app masquerade screen
    } else {
      this.router.navigate(['/select-role']); // modern in-app role picker
    }
  }
}
