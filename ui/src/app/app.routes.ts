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
    path: 'rooms',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/rooms').then((m) => m.Rooms),
  },
  {
    // NB: 'rooms/new' must precede 'rooms/:id' so it isn't captured as an id.
    path: 'rooms/new',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-edit').then((m) => m.RoomEdit),
  },
  {
    // Per-room availability editor (?events=1 for event availability).
    path: 'room-sharing/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-sharing-edit').then((m) => m.RoomSharingEdit),
  },
  {
    path: 'rooms/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-edit').then((m) => m.RoomEdit),
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
    path: 'room-availability',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/room-availability').then((m) => m.RoomAvailability),
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
  // ---- Wave 1: GWT-backed screens ported via multi-agent orchestration ----
  {
    path: 'travel-times',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/travel-times').then((m) => m.TravelTimes),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/change-password').then((m) => m.ChangePassword),
  },
  {
    path: 'scripts',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/scripts').then((m) => m.Scripts),
  },
  {
    path: 'task-scheduler',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/task-scheduler').then((m) => m.TaskScheduler),
  },
  {
    path: 'hql-reports',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reports/hql-reports').then((m) => m.HqlReports),
  },
  {
    path: 'point-in-time-data-reports',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reports/point-in-time-data-reports').then((m) => m.PointInTimeDataReports),
  },
  {
    path: 'limit-and-projection-snapshot',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reports/limit-and-projection-snapshot').then((m) => m.LimitAndProjectionSnapshot),
  },
  {
    path: 'assignment-history',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/assignment-history').then((m) => m.AssignmentHistory),
  },
  {
    path: 'solution-reports',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/solution-reports').then((m) => m.SolutionReports),
  },
  {
    path: 'solver-log',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/solver-log').then((m) => m.SolverLog),
  },
  {
    path: 'saved-timetables',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/list-solutions').then((m) => m.ListSolutions),
  },
  {
    path: 'teaching-assignment-changes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/instructors/teaching-assignment-changes').then((m) => m.TeachingAssignmentChanges),
  },
  {
    path: 'examinations',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/exams').then((m) => m.Exams),
  },
  // ---- Wave 2: larger GWT screens (functional cores) ----
  {
    path: 'personal-timetable',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/personal-timetable').then((m) => m.PersonalTimetable),
  },
  {
    path: 'lookup-classes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/lookup-classes').then((m) => m.LookupClasses),
  },
  {
    path: 'resource-timetable',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/resource-timetable').then((m) => m.ResourceTimetable),
  },
  {
    path: 'batch-sectioning-reports',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/batch-sectioning-reports').then((m) => m.BatchSectioningReports),
  },
  {
    path: 'online-sectioning-reports',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/online-sectioning-reports').then((m) => m.OnlineSectioningReports),
  },
  {
    path: 'batch-sectioning-dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/batch-sectioning-dashboard').then((m) => m.BatchSectioningDashboard),
  },
  {
    path: 'online-sectioning-dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/online-sectioning-dashboard').then((m) => m.OnlineSectioningDashboard),
  },
  {
    path: 'instructor-survey',
    canActivate: [authGuard],
    loadComponent: () => import('./features/instructors/instructor-survey').then((m) => m.InstructorSurvey),
  },
  {
    path: 'scheduling-assistant',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/sectioning').then((m) => m.Sectioning),
  },
  {
    path: 'course-requests',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/course-requests').then((m) => m.CourseRequests),
  },
  {
    path: 'advisor-recommendations',
    canActivate: [authGuard],
    loadComponent: () => import('./features/sectioning/advisor-recommendations').then((m) => m.AdvisorRecommendations),
  },
  { path: '**', redirectTo: 'home' },
];
