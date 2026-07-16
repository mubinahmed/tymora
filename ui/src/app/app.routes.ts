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
  // ---- Wave 3: Events add / detail ----
  {
    path: 'event-add',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/event-add').then((m) => m.EventAdd),
  },
  {
    path: 'event/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/events/event-detail').then((m) => m.EventDetail),
  },
  // ---- Wave 4: generic read-only admin listings (new SimpleListBackend bean) ----
  {
    path: 'list/:page',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/admin-list').then((m) => m.AdminList),
  },
  // ---- Wave 5: search pages + create/edit (new command beans) ----
  {
    path: 'classes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/courses/classes-search').then((m) => m.ClassesSearch),
  },
  {
    path: 'examinations-list',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exams-list').then((m) => m.ExamsList),
  },
  {
    path: 'status-types-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/status-types-edit').then((m) => m.StatusTypesEdit),
  },
  {
    path: 'managers-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/managers-edit').then((m) => m.ManagersEdit),
  },
  {
    path: 'solver-groups-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/solver-groups-edit').then((m) => m.SolverGroupsEdit),
  },
  {
    path: 'sessions-edit/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/sessions-edit').then((m) => m.SessionsEdit),
  },
  // ---- Wave 6: examination reports (read-only, persisted assignments) ----
  {
    path: 'assigned-exams',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/assigned-exams').then((m) => m.AssignedExams),
  },
  {
    path: 'unassigned-exams',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/unassigned-exams').then((m) => m.UnassignedExams),
  },
  {
    path: 'exam-assignment-report',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exam-assignment-report').then((m) => m.ExamAssignmentReport),
  },
  {
    path: 'exam-grid',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exam-grid').then((m) => m.ExamGrid),
  },
  // ---- Wave 7: remaining legacy pages ----
  {
    path: 'change-log',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/change-log').then((m) => m.ChangeLog),
  },
  {
    path: 'manage-solvers',
    canActivate: [authGuard],
    loadComponent: () => import('./features/solver/manage-solvers').then((m) => m.ManageSolvers),
  },
  {
    path: 'instructor-detail/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/instructors/instructor-detail').then((m) => m.InstructorDetail),
  },
  {
    path: 'class-assignments',
    canActivate: [authGuard],
    loadComponent: () => import('./features/courses/class-assignments').then((m) => m.ClassAssignments),
  },
  {
    path: 'distribution-prefs',
    canActivate: [authGuard],
    loadComponent: () => import('./features/courses/distribution-prefs').then((m) => m.DistributionPrefs),
  },
  {
    path: 'exact-time',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/exact-time-edit').then((m) => m.ExactTimeEdit),
  },
  {
    path: 'exam-periods',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exam-periods').then((m) => m.ExamPeriods),
  },
  {
    path: 'application-config',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/application-config').then((m) => m.ApplicationConfig),
  },
  {
    path: 'manager-settings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/manager-settings').then((m) => m.ManagerSettings),
  },
  // ---- Wave 8: solver-proxy exams, projection rules, deferred editors ----
  {
    path: 'exam-changes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exam-changes').then((m) => m.ExamChanges),
  },
  {
    path: 'exam-cbs',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exam-cbs').then((m) => m.ExamCbs),
  },
  {
    path: 'curriculum-projection-rules',
    canActivate: [authGuard],
    loadComponent: () => import('./features/curricula/curriculum-projection-rules').then((m) => m.CurriculumProjectionRules),
  },
  {
    path: 'exam-periods-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/examinations/exam-periods-edit').then((m) => m.ExamPeriodsEdit),
  },
  {
    path: 'distribution-prefs-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/courses/distribution-prefs-edit').then((m) => m.DistributionPrefsEdit),
  },
  {
    path: 'pattern-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/pattern-edit').then((m) => m.PatternEdit),
  },
  {
    path: 'session-create',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/session-create').then((m) => m.SessionCreate),
  },
  {
    path: 'application-config-edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/application-config-edit').then((m) => m.ApplicationConfigEdit),
  },
  { path: '**', redirectTo: 'home' },
];
