import { Injectable, signal } from '@angular/core';

type Mode = 'light' | 'dark';
const STORAGE_KEY = 'ut-theme';

/**
 * Light / dark theme controller. The `dark` class on <html> drives both the
 * app's own --ut-* tokens (styles.scss) and PrimeNG's dark scheme (configured
 * darkModeSelector: '.dark'). The initial class is applied by a tiny inline
 * script in index.html to avoid a flash of the wrong theme before Angular boots;
 * this service reads that state, then owns toggling + persistence.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  /** True when dark mode is active. */
  readonly isDark = signal(this.readInitial());

  toggle(): void {
    this.set(!this.isDark());
  }

  set(dark: boolean): void {
    this.isDark.set(dark);
    document.documentElement.classList.toggle('dark', dark);
    try {
      localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
    } catch {
      /* storage unavailable (private mode) — the in-memory signal still works */
    }
  }

  private readInitial(): boolean {
    // Trust the class the inline boot script already set, if present.
    if (typeof document !== 'undefined' && document.documentElement.classList.contains('dark')) return true;
    try {
      const stored = localStorage.getItem(STORAGE_KEY) as Mode | null;
      if (stored) return stored === 'dark';
    } catch {
      /* ignore */
    }
    return typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches;
  }
}
