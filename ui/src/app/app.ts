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

  ngOnInit(): void {
    this.auth.ensureLoaded().subscribe(() => {
      if (this.auth.isAuthenticated()) this.menu.load();
    });
  }

  logout(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/signin']));
  }
}
