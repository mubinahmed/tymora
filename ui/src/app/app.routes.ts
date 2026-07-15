import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    // NB: /signin (not /login) to avoid colliding with Spring Security's /login POST URL.
    path: 'signin',
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'buildings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/buildings').then((m) => m.Buildings),
  },
  {
    path: 'roomgroups',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-groups').then((m) => m.RoomGroups),
  },
  {
    path: 'roomfeatures',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-features').then((m) => m.RoomFeatures),
  },
  {
    path: 'departments',
    canActivate: [authGuard],
    loadComponent: () => import('./features/departments/departments').then((m) => m.Departments),
  },
  {
    path: 'admin/:type',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/simple-edit').then((m) => m.SimpleEdit),
  },
  {
    path: 'reservations',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reservations/reservations').then((m) => m.Reservations),
  },
  {
    path: 'curricula',
    canActivate: [authGuard],
    loadComponent: () => import('./features/curricula/curricula').then((m) => m.Curricula),
  },
  {
    path: 'offerings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/offerings/offerings').then((m) => m.Offerings),
  },
  {
    path: 'instructorattributes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/instructors/instructor-attributes').then((m) => m.InstructorAttributes),
  },
  {
    path: 'teachingRequests',
    canActivate: [authGuard],
    loadComponent: () => import('./features/instructors/teaching-requests').then((m) => m.TeachingRequests),
  },
  {
    path: 'teachingAssignments',
    canActivate: [authGuard],
    loadComponent: () => import('./features/instructors/teaching-assignments').then((m) => m.TeachingAssignments),
  },
  {
    path: 'events',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/events').then((m) => m.Events),
  },
  {
    path: 'solver',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/solver').then((m) => m.Solver),
  },
  {
    path: 'assignedClasses',
    canActivate: [authGuard],
    data: { rpc: 'AssignedClassesRequest', title: 'Assigned Classes' },
    loadComponent: () => import('./features/solver/solver-report').then((m) => m.SolverReport),
  },
  {
    path: 'notAssignedClasses',
    canActivate: [authGuard],
    data: { rpc: 'NotAssignedClassesRequest', title: 'Not-assigned Classes' },
    loadComponent: () => import('./features/solver/solver-report').then((m) => m.SolverReport),
  },
  {
    path: 'solutionChanges',
    canActivate: [authGuard],
    data: { rpc: 'SolutionChangesRequest', title: 'Solution Changes' },
    loadComponent: () => import('./features/solver/solver-report').then((m) => m.SolverReport),
  },
  {
    path: 'timetableGrid',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/timetable-grid').then((m) => m.TimetableGrid),
  },
  {
    path: 'cbs',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/cbs').then((m) => m.Cbs),
  },
  {
    path: 'publishedSolutions',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/published-solutions').then((m) => m.PublishedSolutions),
  },
  {
    path: 'chameleon',
    canActivate: [authGuard],
    loadComponent: () => import('./features/chameleon/chameleon').then((m) => m.Chameleon),
  },
  {
    // Reached from the offerings search (or the legacy detail during coexistence).
    path: 'course-offering/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/offerings/course-offering-edit').then((m) => m.CourseOfferingEdit),
  },
  { path: '**', redirectTo: 'home' },
];
