import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * Tymora design-system preset (derived from the "Add Event" Claude Design mock).
 *
 * Two palettes carry the whole look:
 *  - primary: a navy→sky blue ramp (#042C53 … #378ADD … #E6F1FB) used for actions,
 *    links, focus rings and highlights.
 *  - surface: a warm cream/greige neutral ramp used for page backgrounds, card
 *    borders and text — this is what gives the UI its parchment feel instead of
 *    the stock cool grey.
 *
 * Overriding these on the Aura preset re-skins every PrimeNG component (buttons,
 * inputs, selects, tables, dialogs, menus, cards …) app-wide.
 */
export const UniTimePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#E6F1FB',
      100: '#C4DDF5',
      200: '#9EC6EE',
      300: '#78AFE7',
      400: '#5A9DE2',
      500: '#378ADD',
      600: '#2E75C4',
      700: '#185FA5',
      800: '#0E477F',
      900: '#042C53',
      950: '#03203D',
    },
    focusRing: {
      width: '3px',
      style: 'solid',
      color: 'rgba(55, 138, 221, 0.35)',
      offset: '0',
    },
    colorScheme: {
      light: {
        primary: {
          color: '{primary.500}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.700}',
          activeColor: '{primary.800}',
        },
        highlight: {
          background: '{primary.50}',
          focusBackground: '{primary.100}',
          color: '{primary.900}',
          focusColor: '{primary.900}',
        },
        surface: {
          0: '#ffffff',
          50: '#FAF9F4',
          100: '#F5F4EF',
          200: '#EDEBE4',
          300: '#DCDAD2',
          400: '#C6C4BB',
          500: '#B4B2A9',
          600: '#888780',
          700: '#4A4944',
          800: '#33322E',
          900: '#1F1E1B',
          950: '#121110',
        },
      },
      // Dark scheme: lighten the blue accent so it reads on dark surfaces; the
      // component surfaces (inputs, cards, tables, menus) come from Aura's dark
      // slate ramp, which the app's --ut-* dark tokens (styles.scss) coordinate with.
      dark: {
        primary: {
          color: '{primary.400}',
          contrastColor: '#04121f',
          hoverColor: '{primary.300}',
          activeColor: '{primary.200}',
        },
        highlight: {
          background: 'rgba(55, 138, 221, 0.16)',
          focusBackground: 'rgba(55, 138, 221, 0.24)',
          color: '{primary.100}',
          focusColor: '#ffffff',
        },
      },
    },
  },
});
