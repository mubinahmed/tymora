import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { MessageService } from 'primeng/api';

import { routes } from './app.routes';
import { apiInterceptor } from './core/http.interceptor';
import { UniTimePreset } from './theme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withInterceptors([apiInterceptor]),
      // Spring Security's cookie/header names for CSRF on state-changing calls.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    providePrimeNG({
      theme: {
        preset: UniTimePreset,
        // Dark mode activates when <html> has the `dark` class (ThemeService).
        options: { darkModeSelector: '.dark' },
      },
    }),
    MessageService,
  ],
};
