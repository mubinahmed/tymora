// AUTO-GENERATED from org.unitime.timetable.gwt.shared by TsModelGenerator.
// Do not edit by hand. Field names follow the facade's Gson naming (iField -> field).

/** org.unitime.timetable.gwt.client.access.AccessControlInterface.Operation */
export type AccessControlInterface_Operation = 'PING' | 'CHECK_ACCESS' | 'LOGOUT';
/** org.unitime.timetable.gwt.shared.TableInterface.Alignment */
export type Alignment = 'LEFT' | 'CENTER' | 'RIGHT';
/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPictureRequest.Apply */
export type Apply = 'THIS_SESSION_ONLY' | 'ALL_FUTURE_SESSIONS' | 'ALL_SESSIONS';
/** org.unitime.timetable.gwt.shared.EventInterface.ApprovalStatus */
export type ApprovalStatus = 'Pending' | 'Approved' | 'Rejected' | 'Cancelled' | 'Deleted';
/** org.unitime.timetable.gwt.shared.InstructorInterface.ChangesType */
export type ChangesType = 'INITIAL' | 'BEST' | 'SAVED';
/** org.unitime.timetable.gwt.shared.StudentSchedulingPreferencesInterface.ClassModality */
export type ClassModality = 'NoPreference' | 'DiscouragedOnline' | 'PreferredOnline' | 'RequiredOnline';
/** org.unitime.timetable.gwt.shared.EventInterface.FilterRpcRequest.Command */
export type Command = 'LOAD' | 'SUGGESTIONS' | 'ENUMERATE';
/** org.unitime.timetable.gwt.shared.EventInterface.EventType */
export type EventType = 'Class' | 'FinalExam' | 'MidtermExam' | 'Course' | 'Special' | 'Unavailabile' | 'Message';
/** org.unitime.timetable.gwt.shared.TaskInterface.ExecutionStatus */
export type ExecutionStatus = 'CREATED' | 'QUEUED' | 'RUNNING' | 'FINISHED' | 'FAILED';
/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.FieldType */
export type FieldType = 'textarea' | 'number' | 'toggle' | 'list' | 'add' | 'delete' | 'hasError';
/** org.unitime.timetable.gwt.shared.InstrOfferingConfigInterface.Operation */
export type InstrOfferingConfigInterface_Operation = 'LOAD' | 'SAVE' | 'DELETE';
/** org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectLookupRpcRequest.Level */
export type Level = 'SESSION' | 'SUBJECT' | 'OFFERING' | 'COURSE' | 'CONFIG' | 'SUBPART' | 'CLASS' | 'NONE';
/** org.unitime.timetable.gwt.shared.EventInterface.MessageInterface.Level */
export type MessageInterface_Level = 'INFO' | 'WARN' | 'ERROR';
/** org.unitime.timetable.gwt.shared.EventInterface.NoteInterface.NoteType */
export type NoteType = 'Create' | 'AddMeetings' | 'Approve' | 'Reject' | 'Delete' | 'Edit' | 'Inquire' | 'Cancel' | 'Email';
/** org.unitime.timetable.gwt.shared.ClassSetupInterface.Operation */
export type Operation = 'LOAD' | 'SAVE';
/** org.unitime.timetable.gwt.shared.SolverInterface.PageMessageType */
export type PageMessageType = 'INFO' | 'WARNING' | 'ERROR';
/** org.unitime.timetable.gwt.shared.RoomInterface.PeriodPreferenceRequest.Operation */
export type PeriodPreferenceRequest_Operation = 'LOAD' | 'SAVE' | 'LOAD_FOR_EXAM';
/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.Problem */
export type Problem = 'NOT_APPLIED' | 'LEVEL_CHANGED' | 'NOT_IN_SURVEY' | 'DIFFERENT_DEPT';
/** org.unitime.timetable.gwt.shared.SolverInterface.ProgressLogLevel */
export type ProgressLogLevel = 'TRACE' | 'DEBUG' | 'PROGRESS' | 'INFO' | 'STAGE' | 'WARN' | 'ERROR' | 'FATAL';
/** org.unitime.timetable.gwt.shared.PublishedSectioningSolutionInterface.Operation */
export type PublishedSectioningSolutionInterface_Operation = 'LIST' | 'REMOVE' | 'LOAD' | 'UNLOAD' | 'PUBLISH' | 'UNPUBLISH' | 'SELECT' | 'DESELECT' | 'NOTE';
/** org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectInterface.RelatedObjectType */
export type RelatedObjectType = 'Offering' | 'Course' | 'Config' | 'Class' | 'Examination';
/** org.unitime.timetable.gwt.shared.CourseRequestInterface.RequestedCourseStatus */
export type RequestedCourseStatus = 'NEW_REQUEST' | 'ENROLLED' | 'SAVED' | 'OVERRIDE_APPROVED' | 'OVERRIDE_CANCELLED' | 'OVERRIDE_PENDING' | 'OVERRIDE_NEEDED' | 'OVERRIDE_REJECTED' | 'CREDIT_LOW' | 'CREDIT_HIGH' | 'OVERRIDE_NOT_NEEDED' | 'WAITLIST_INACTIVE';
/** org.unitime.timetable.gwt.shared.EventInterface.ResourceType */
export type ResourceType = 'ROOM' | 'SUBJECT' | 'CURRICULUM' | 'DEPARTMENT' | 'PERSON' | 'COURSE' | 'GROUP';
/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPictureRequest.Operation */
export type RoomPictureRequest_Operation = 'LOAD' | 'SAVE' | 'UPLOAD';
/** org.unitime.timetable.gwt.shared.RoomInterface.RoomSharingRequest.Operation */
export type RoomSharingRequest_Operation = 'LOAD' | 'SAVE';
/** org.unitime.timetable.gwt.shared.RoomInterface.RoomUpdateRpcRequest.Operation */
export type RoomUpdateRpcRequest_Operation = 'CREATE' | 'UPDATE' | 'DELETE';
/** org.unitime.timetable.gwt.shared.EventInterface.SaveOrApproveEventRpcRequest.Operation */
export type SaveOrApproveEventRpcRequest_Operation = 'APPROVE' | 'REJECT' | 'INQUIRE' | 'CREATE' | 'UPDATE' | 'DELETE' | 'CANCEL' | 'EMAIL';
/** org.unitime.timetable.gwt.shared.StudentSchedulingPreferencesInterface.ScheduleGaps */
export type ScheduleGaps = 'NoPreference' | 'PreferBackToBack' | 'DiscourageBackToBack';
/** org.unitime.timetable.gwt.shared.SimpleEditInterface.FieldType */
export type SimpleEditInterface_FieldType = 'text' | 'textarea' | 'number' | 'toggle' | 'list' | 'multi' | 'students' | 'person' | 'date' | 'parent' | 'time';
/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolutionOperation */
export type SolutionOperation = 'INIT' | 'CHECK' | 'SELECT' | 'DESELECT' | 'LOAD' | 'LOAD_EMPTY' | 'UNLOAD' | 'COMMIT' | 'UNCOMMIT' | 'EXPORT' | 'UPDATE_NOTE' | 'DELETE' | 'RELOAD' | 'SAVE' | 'SAVE_AS_NEW' | 'SAVE_COMMIT' | 'SAVE_AS_NEW_COMMIT';
/** org.unitime.timetable.gwt.shared.SolverInterface.SolverOperation */
export type SolverOperation = 'INIT' | 'CHECK' | 'LOAD' | 'START' | 'UNLOAD' | 'RELOAD' | 'STOP' | 'CLEAR' | 'EXPORT_CSV' | 'EXPORT_XML' | 'STUDENT_SECTIONING' | 'SAVE_BEST' | 'RESTORE_BEST' | 'SAVE' | 'SAVE_AS_NEW' | 'SAVE_COMMIT' | 'SAVE_AS_NEW_COMMIT' | 'SAVE_UNCOMMIT' | 'VALIDATE' | 'PUBLISH' | 'UNPUBLISH' | 'CLONE';
/** org.unitime.timetable.gwt.shared.SolverInterface.SolverType */
export type SolverType = 'COURSE' | 'EXAM' | 'STUDENT' | 'INSTRUCTOR';
/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationOperation */
export type SpecialRegistrationOperation = 'Add' | 'Drop' | 'Keep';
/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationStatus */
export type SpecialRegistrationStatus = 'Draft' | 'Pending' | 'Approved' | 'Rejected' | 'Cancelled';
/** org.unitime.timetable.gwt.client.rooms.TravelTimes.TravelTimesRequest.Command */
export type TravelTimesRequest_Command = 'INIT' | 'LOAD' | 'SAVE';
/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridCell.Type */
export type Type = 'Class' | 'Event';
/** org.unitime.timetable.gwt.shared.RoomInterface.UpdateBuildingAction */
export type UpdateBuildingAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'UPDATE_DATA';
/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.UpdateCourseOfferingAction */
export type UpdateCourseOfferingAction = 'CREATE' | 'UPDATE';
/** org.unitime.timetable.gwt.shared.DepartmentInterface.UpdateDepartmentAction */
export type UpdateDepartmentAction = 'CREATE' | 'UPDATE' | 'DELETE';
/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.WaitListMode */
export type WaitListMode = 'WaitList' | 'NoSubs' | 'None';

/** org.unitime.timetable.gwt.shared.CurriculumInterface.AcademicAreaInterface */
export interface AcademicAreaInterface {
  areaId?: number;
  areaAbbv?: string;
  areaName?: string;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.AcademicClassificationInterface */
export interface AcademicClassificationInterface {
  clasfId?: number;
  clasfCode?: string;
  clasfName?: string;
}

/** org.unitime.timetable.gwt.client.events.AcademicSessionSelectionBox.AcademicSession */
export interface AcademicSession {
  uniqueId?: number;
  name?: string;
  abbv?: string;
  hint?: string;
  selected?: boolean;
  previousId?: number;
  nextId?: number;
  flags?: number;
}

/** org.unitime.timetable.gwt.shared.AcademicSessionProvider.AcademicSessionInfo */
export interface AcademicSessionInfo {
  sessionId?: number;
  year?: string;
  term?: string;
  campus?: string;
  name?: string;
  externalTerm?: string;
  externalCampus?: string;
  startDate?: string;
  primary?: boolean;
  online?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.AcademicSessionInterface */
export interface AcademicSessionInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.AdvisedInfoInterface */
export interface AdvisedInfoInterface {
  minCredit?: number;
  maxCredit?: number;
  percentage?: number;
  message?: string;
  notAssignedMessage?: string;
  missingCritical?: number;
  missingPrimary?: number;
  notAssignedCritical?: number;
  notAssignedPrimary?: number;
  advisorCritical?: number;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.AdvisingStudentDetails */
export interface AdvisingStudentDetails {
  studentId?: number;
  sessionId?: number;
  studentName?: string;
  studentExternalId?: string;
  studentEmail?: string;
  sessionName?: string;
  advisorEmail?: string;
  currentStatus?: StudentStatusInfo;
  availableStatuses?: StudentStatusInfo[];
  canUpdate?: boolean;
  degreePlan?: boolean;
  canEmail?: boolean;
  request?: CourseRequestInterface;
  studentRequest?: CourseRequestInterface;
  emailOptionalToggleCaption?: string;
  emailOptionalToggleDefault?: boolean;
  mode?: WaitListMode;
  canRequire?: boolean;
  advisorWaitListedCourseIds?: number[];
  criticalCheck?: number;
  classScheduleNotAvailable?: boolean;
  otherSessionRecommendations?: { [key: string]: CourseRequestInterface };
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.AdvisorCourseRequestSubmission */
export interface AdvisorCourseRequestSubmission {
  pdf?: number[];
  updated?: boolean;
  name?: string;
  link?: string;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.AdvisorNote */
export interface AdvisorNote {
  displayString?: string;
  replaceString?: string;
  count?: number;
  timeStamp?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.ApproveEventRpcRequest */
export interface ApproveEventRpcRequest extends SaveOrApproveEventRpcRequest {
  operation?: SaveOrApproveEventRpcRequest_Operation;
  meetings?: MeetingInterface[];
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Area */
export interface Area extends IdName {
  classifications?: IdName[];
  majors?: IdName[];
  minors?: IdName[];
  concentrations?: IdName[];
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface */
export interface AssignClassInstructorsInterface {
  records?: Record[];
  fields?: Field[];
  editable?: boolean;
  pageName?: PageName;
  configId?: number;
  offeringId?: number;
  saveSuccessful?: boolean;
  errors?: string;
  nextConfigId?: number;
  previousConfigId?: number;
  courseName?: string;
  courseCoordinators?: string;
  showTimeAndRoom?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignedClassesFilterRequest */
export interface AssignedClassesFilterRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignedClassesFilterResponse */
export interface AssignedClassesFilterResponse extends FilterInterface {
  preferences?: PreferenceInterface[];
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignedClassesRequest */
export interface AssignedClassesRequest {
  filter?: FilterInterface;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignedClassesResponse */
export interface AssignedClassesResponse extends TableInterface {
  pageMessages?: PageMessage[];
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.AssignmentChangesRequest */
export interface AssignmentChangesRequest {
  type?: ChangesType;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.AssignmentChangesResponse */
export interface AssignmentChangesResponse {
  changes?: AssignmentInfo[];
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignmentHistoryFilterRequest */
export interface AssignmentHistoryFilterRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignmentHistoryFilterResponse */
export interface AssignmentHistoryFilterResponse extends AssignedClassesFilterResponse {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignmentHistoryRequest */
export interface AssignmentHistoryRequest {
  filter?: FilterInterface;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.AssignmentHistoryResponse */
export interface AssignmentHistoryResponse extends SolutionChangesResponse {
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.AssignmentInfo */
export interface AssignmentInfo {
  request?: TeachingRequestInfo;
  index?: number;
  instructor?: InstructorInfo;
  conflicts?: string[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.AttachmentTypeInterface */
export interface AttachmentTypeInterface {
  id?: number;
  abbreviation?: string;
  label?: string;
  image?: boolean;
  tooltip?: boolean;
  table?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.AttributeInterface */
export interface AttributeInterface {
  id?: number;
  parentId?: number;
  code?: string;
  name?: string;
  parentName?: string;
  department?: InstructorInterface_DepartmentInterface;
  type?: AttributeTypeInterface;
  instructors?: InstructorInterface2[];
  canEdit?: boolean;
  canDelete?: boolean;
  canAssign?: boolean;
  canChangeType?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.AttributeTypeInterface */
export interface AttributeTypeInterface {
  id?: number;
  abbv?: string;
  label?: string;
  conjunctive?: boolean;
  required?: boolean;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.BtbInstructorInfo */
export interface BtbInstructorInfo {
  other?: ClassAssignmentDetails;
  another?: ClassAssignmentDetails;
  pref?: number;
}

/** org.unitime.timetable.gwt.client.rooms.TravelTimes.Building */
export interface Building {
  id?: number;
  name?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.BuildingCheckCanDeleteRequest */
export interface BuildingCheckCanDeleteRequest {
  buildingId?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.BuildingInterface */
export interface BuildingInterface {
  id?: number;
  abbreviation?: string;
  name?: string;
  x?: number;
  y?: number;
  externalId?: string;
  canEdit?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.BuildingsDataResponse */
export interface BuildingsDataResponse {
  buildings?: BuildingInterface[];
  canAdd?: boolean;
  canExportPDF?: boolean;
  canUpdateData?: boolean;
  ellipsoid?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.CBSNode */
export interface CBSNode {
  count?: number;
  name?: string;
  hTML?: string;
  link?: string;
  pref?: string;
  nodes?: CBSNode[];
  classId?: number;
  selection?: SelectedAssignment;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.CancelSpecialRegistrationRequest */
export interface CancelSpecialRegistrationRequest extends StudentSectioningContext {
  requestId?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.CancelSpecialRegistrationResponse */
export interface CancelSpecialRegistrationResponse {
  success?: boolean;
  message?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.ChangeGradeModesRequest */
export interface ChangeGradeModesRequest extends StudentSectioningContext {
  changes?: SpecialRegistrationGradeModeChange[];
  creditChanges?: SpecialRegistrationCreditChange[];
  maxCredit?: number;
  currentCredit?: number;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.ChangeGradeModesResponse */
export interface ChangeGradeModesResponse {
  gradeModes?: GradeModes;
  requests?: RetrieveSpecialRegistrationResponse[];
  cancelRequestIds?: string[];
}

/** org.unitime.timetable.gwt.shared.LastChangesInterface.ChangeLogInterface */
export interface ChangeLogInterface {
  id?: number;
  page?: string;
  object?: string;
  operation?: string;
  manager?: string;
  date?: string;
  department?: string;
  departmentId?: number;
  subject?: string;
  subjectId?: number;
  session?: string;
  sessionDate?: string;
  sessionInitiative?: string;
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.CheckCoursesResponse */
export interface CheckCoursesResponse {
  messages?: CourseMessage[];
  confirmationSetup?: { [key: string]: string[] };
  errorMessage?: string;
  creditWarning?: string;
  creditNote?: string;
  maxCreditNeeded?: number;
  maxCreditOverrideStatus?: RequestedCourseStatus;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.ClassAssignment */
export interface ClassAssignment {
  courseAssigned?: boolean;
  courseId?: number;
  classId?: number;
  subpartId?: number;
  days?: number[];
  start?: number;
  length?: number;
  breakTime?: number;
  instructos?: string[];
  instructoEmails?: string[];
  rooms?: ClassAssignmentInterface_IdValue[];
  alternative?: boolean;
  hasAlternatives?: boolean;
  distanceConflict?: boolean;
  teachingAssigment?: boolean;
  instructing?: boolean;
  datePattern?: string;
  subject?: string;
  courseNbr?: string;
  subpart?: string;
  section?: string;
  parentSection?: string;
  number?: string;
  title?: string;
  limit?: number[];
  available?: boolean;
  pin?: boolean;
  backToBackDistance?: number;
  backToBackRooms?: string;
  saved?: boolean;
  dummy?: boolean;
  cancelled?: boolean;
  expected?: number;
  overlapNote?: string;
  note?: string;
  credit?: string;
  error?: string;
  warn?: string;
  info?: string;
  enrolledDate?: string;
  externalId?: string;
  specRegStatus?: SpecialRegistrationStatus;
  specRegOperation?: SpecialRegistrationOperation;
  gradeMode?: GradeMode;
  creditHour?: number;
  creditMin?: number;
  creditMax?: number;
  canWaitList?: boolean;
  longDistanceConflict?: boolean;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.ClassAssignmentDetails */
export interface ClassAssignmentDetails {
  class?: SuggestionsInterface_ClassInfo;
  time?: TimeInfo;
  room?: RoomInfo[];
  instructor?: SuggestionsInterface_InstructorInfo[];
  initialTime?: TimeInfo;
  initialRoom?: RoomInfo[];
  assignedTime?: TimeInfo;
  assignedRoom?: RoomInfo[];
  rooms?: RoomInfo[];
  times?: TimeInfo[];
  studentConflicts?: StudentConflictInfo[];
  distributionConflicts?: DistributionInfo[];
  btbInstructorConflicts?: BtbInstructorInfo[];
  conflict?: string;
  objectives?: { [key: string]: number };
  assignedObjectives?: { [key: string]: number };
  canUnassign?: boolean;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.ClassAssignmentDetailsRequest */
export interface ClassAssignmentDetailsRequest {
  classId?: number;
  assignments?: SelectedAssignment[];
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface */
export interface ClassAssignmentInterface {
  assignments?: CourseAssignment[];
  messages?: string[];
  errors?: ErrorMessage[];
  notes?: Note[];
  specialRegistrations?: RetrieveSpecialRegistrationResponse[];
  canEnroll?: boolean;
  canSetCriticalOverrides?: boolean;
  value?: number;
  currentCredit?: number;
  request?: CourseRequestInterface;
  advisorRequest?: CourseRequestInterface;
  advisorWaitListedCourseIds?: number[];
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.IdValue */
export interface ClassAssignmentInterface_IdValue {
  id?: number;
  value?: string;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.StudentInfo */
export interface ClassAssignmentInterface_StudentInfo {
  student?: Student;
  enrollment?: number;
  waitlist?: number;
  reservation?: number;
  requested?: number;
  unassigned?: number;
  noSub?: number;
  swap?: number;
  totalEnrollment?: number;
  totalWaitlist?: number;
  totalReservation?: number;
  totalUnassigned?: number;
  totalNoSub?: number;
  totalSwap?: number;
  consentNeeded?: number;
  totalConsentNeeded?: number;
  topWaitingPriority?: number;
  requestedDate?: string;
  enrolledDate?: string;
  approvedDate?: string;
  emailDate?: string;
  waitListedDate?: string;
  status?: string;
  note?: string;
  credit?: number;
  totalCredit?: number;
  iMCredit?: { [key: string]: number };
  iMTotalCredit?: { [key: string]: number };
  nrDistanceConflicts?: number;
  longestDistanceMinutes?: number;
  overlappingMinutes?: number;
  totalNrDistanceConflicts?: number;
  totalLongestDistanceMinutes?: number;
  totalOverlappingMinutes?: number;
  freeTimeOverlappingMins?: number;
  totalFreeTimeOverlappingMins?: number;
  prefInstrMethConflict?: number;
  totalPrefInstrMethConflict?: number;
  prefSectionConflict?: number;
  totalPrefSectionConflict?: number;
  requestCredit?: number[];
  requestTotalCredit?: number[];
  overrideNeeded?: number;
  totalOverrideNeeded?: number;
  myStudent?: boolean;
  advised?: AdvisedInfoInterface;
  preference?: string;
  pin?: string;
  pinReleased?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.ClassInfo */
export interface ClassInfo {
  courseId?: number;
  classId?: number;
  course?: string;
  section?: string;
  type?: string;
  externalId?: string;
  room?: string;
  instructor?: boolean;
  time?: string;
  date?: string;
}

/** org.unitime.timetable.gwt.shared.ClassSetupInterface.ClassLine */
export interface ClassLine {
  classId?: number;
  subpartId?: number;
  iType?: number;
  editable?: boolean;
  editableDatePattern?: boolean;
  canCancel?: boolean;
  canDelete?: boolean;
  enrollment?: number;
  snapshotLimit?: number;
  minClassLimit?: number;
  maxClassLimit?: number;
  numberOfRooms?: number;
  displayInstructors?: boolean;
  enabledForStudentScheduling?: boolean;
  roomRatio?: number;
  parentId?: number;
  departmentId?: number;
  datePatternId?: number;
  cancelled?: boolean;
  lMS?: number;
  splitAttendance?: boolean;
  time?: string;
  date?: string;
  rooms?: Reference[];
  instructor?: string;
  externalId?: string;
  indent?: number;
  label?: string;
  subpartLabel?: string;
  error?: string;
}

/** org.unitime.timetable.gwt.shared.ClassSetupInterface */
export interface ClassSetupInterface {
  classLines?: ClassLine[];
  departments?: Reference[];
  instructionalMethods?: Reference[];
  lMSs?: Reference[];
  datePatterns?: Reference[];
  subparts?: Subpart[];
  configId?: number;
  limit?: number;
  unlimited?: boolean;
  offeringId?: number;
  displayOptionForMaxLimit?: boolean;
  displayMaxLimit?: boolean;
  displayInstructors?: boolean;
  displayEnabledForStudentScheduling?: boolean;
  displayExternalId?: boolean;
  editExternalId?: boolean;
  editSnapshotLimits?: boolean;
  instructionalMethodId?: number;
  instructionalMethodEditable?: boolean;
  displayLms?: boolean;
  name?: string;
  displayEnrollments?: boolean;
  editUnlimited?: boolean;
  displaySnapshotLimit?: boolean;
  validateLimits?: boolean;
  operation?: Operation;
  lastGeneratedId?: number;
  hasTimeRooms?: boolean;
  hasInstructors?: boolean;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Clazz */
export interface Clazz extends IdName {
  subpart?: ReservationInterface_Subpart;
  externalId?: string;
  cancelled?: boolean;
  time?: string;
  date?: string;
  room?: string;
  instructor?: string;
  enrollment?: number;
}

/** org.unitime.timetable.gwt.client.admin.ClearHibernateCache.ClearHibernateCacheRequest */
export interface ClearHibernateCacheRequest {
}

/** org.unitime.timetable.gwt.client.admin.ClearHibernateCache.ClearHibernateCacheResponse */
export interface ClearHibernateCacheResponse {
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.CodeLabel */
export interface CodeLabel {
  code?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.ComputeConflictTableRequest */
export interface ComputeConflictTableRequest {
  classId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.ComputeSuggestionsRequest */
export interface ComputeSuggestionsRequest {
  assignments?: AssignmentInfo[];
  selectedRequestId?: number;
  selectedIndex?: number;
  selectedInstructorId?: number;
  maxDepth?: number;
  timeout?: number;
  maxDomain?: number;
  maxResults?: number;
  computeDomain?: boolean;
  computeSuggestions?: boolean;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Config */
export interface Config extends IdName {
  subparts?: ReservationInterface_Subpart[];
  instructionalMethod?: string;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.Conflict */
export interface Conflict {
  name?: string;
  type?: string;
  date?: string;
  time?: string;
  room?: string;
  style?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.ConflictBasedStatisticsRequest */
export interface ConflictBasedStatisticsRequest {
  classId?: number;
  variableOriented?: boolean;
  limit?: number;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.ConflictStatisticsFilterRequest */
export interface ConflictStatisticsFilterRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.ConflictStatisticsFilterResponse */
export interface ConflictStatisticsFilterResponse extends FilterInterface {
  properties?: SuggestionProperties;
  pageMessages?: PageMessage[];
}

/** org.unitime.timetable.gwt.shared.EventInterface.ContactInterface */
export interface ContactInterface {
  firstName?: string;
  middleName?: string;
  lastName?: string;
  title?: string;
  formattedName?: string;
  externalId?: string;
  email?: string;
  phone?: string;
  responsibilityAbbreviation?: string;
  responsibility?: string;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CoordinatorInterface */
export interface CoordinatorInterface {
  instructorId?: string;
  responsibilityId?: string;
  percShare?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.Course */
export interface Course extends CourseInterface {
  id?: number;
  courseTitle?: string;
  customs?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.CourseAssignment */
export interface CourseAssignment {
  courseId?: number;
  assigned?: boolean;
  teachingAssigment?: boolean;
  subject?: string;
  courseNbr?: string;
  title?: string;
  note?: string;
  creditText?: string;
  creditAbbv?: string;
  hasUniqueName?: boolean;
  hasCrossList?: boolean;
  limit?: number;
  projected?: number;
  enrollment?: number;
  lastLike?: number;
  requested?: number;
  snapShotLimit?: number;
  overlaps?: string[];
  notAvailable?: boolean;
  full?: boolean;
  locked?: boolean;
  canWaitList?: boolean;
  hasIncompReqs?: boolean;
  instead?: string;
  enrollmentMessage?: string;
  conflictMessage?: string;
  requestedDate?: string;
  waitListedDate?: string;
  selection?: number;
  overMaxCredit?: number;
  overrides?: CodeLabel[];
  assignments?: ClassAssignment[];
  instructionalMethods?: ClassAssignmentInterface_IdValue[];
  hasNoInstructionalMethod?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseCreditFormatInterface */
export interface CourseCreditFormatInterface {
  id?: number;
  label?: string;
  reference?: string;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseCreditTypeInterface */
export interface CourseCreditTypeInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseCreditUnitTypeInterface */
export interface CourseCreditUnitTypeInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.CourseDetail */
export interface CourseDetail {
  detail?: string;
}

/** org.unitime.timetable.gwt.client.sectioning.CourseDetailsWidget.CourseDetailsRpcRequest */
export interface CourseDetailsRpcRequest {
  courseId?: number;
  subjectId?: number;
  courseNumber?: string;
}

/** org.unitime.timetable.gwt.client.sectioning.CourseDetailsWidget.CourseDetailsRpcResponse */
export interface CourseDetailsRpcResponse {
  link?: string;
  details?: string;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.CourseInfo */
export interface CourseInfo {
  courseId?: number;
  courseName?: string;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.CourseInterface */
export interface CourseInterface {
  courseId?: number;
  courseName?: string;
  curriculumCourses?: CurriculumCourseInterface[];
  groups?: CurriculumCourseGroupInterface[];
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.CourseMessage */
export interface CourseMessage {
  courseId?: number;
  course?: string;
  error?: boolean;
  message?: string;
  code?: string;
  confirm?: number;
  order?: number;
  status?: RequestedCourseStatus;
  suggestions?: string[];
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingCheckExists */
export interface CourseOfferingCheckExists {
  subjectAreaId?: number;
  courseNumber?: string;
  isEdit?: boolean;
  courseOfferingId?: number;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingCheckExistsInterface */
export interface CourseOfferingCheckExistsInterface {
  responseText?: string;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingCheckPermissions */
export interface CourseOfferingCheckPermissions {
  courseOfferingId?: number;
  subjAreaId?: number;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface */
export interface CourseOfferingInterface {
  id?: number;
  abbreviation?: string;
  name?: string;
  externalId?: string;
  canEdit?: boolean;
  uniqueId?: number;
  isControl?: boolean;
  permId?: string;
  projectedDemand?: number;
  nbrExpectedStudents?: number;
  demand?: number;
  enrollment?: number;
  reservation?: number;
  byReservationOnly?: boolean;
  subjectAreaAbbv?: string;
  courseNbr?: string;
  title?: string;
  scheduleBookNote?: string;
  externalUniqueId?: string;
  label?: string;
  lastWeekToEnroll?: number;
  lastWeekToChange?: number;
  lastWeekToDrop?: number;
  notes?: string;
  consent?: number;
  consentText?: string;
  demandOfferingId?: number;
  demandOfferingText?: string;
  alternativeCourseOfferingId?: number;
  fundingDepartmentId?: number;
  effectiveFundingDepartmentId?: number;
  courseTypeId?: number;
  waitList?: number;
  creditFormat?: string;
  creditText?: string;
  creditType?: number;
  creditUnitType?: number;
  units?: number;
  maxUnits?: number;
  fractionalIncrementsAllowed?: boolean;
  ioNotOffered?: boolean;
  coordinators?: CoordinatorInterface[];
  overrides?: string[];
  catalogLinkLocation?: string;
  catalogLinkLabel?: string;
  errorMessage?: string;
  sendCoordinators?: CoordinatorInterface[];
  subjectAreaId?: number;
  instrOfferingId?: number;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingPermissionsInterface */
export interface CourseOfferingPermissionsInterface {
  canAddCourseOffering?: boolean;
  canEditCourseOffering?: boolean;
  canEditCourseOfferingNote?: boolean;
  canEditCourseOfferingCoordinators?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingPropertiesInterface */
export interface CourseOfferingPropertiesInterface {
  session?: AcademicSessionInterface;
  subjectAreas?: SubjectAreaInterface[];
  courseCreditFormats?: CourseCreditFormatInterface[];
  courseCreditTypes?: CourseCreditTypeInterface[];
  courseCreditUnitTypes?: CourseCreditUnitTypeInterface[];
  courseDemands?: CourseOfferingInterface[];
  altCourseOfferings?: CourseOfferingInterface[];
  courseTypes?: CourseTypeInterface[];
  overrideTypes?: OverrideTypeInterface[];
  waitLists?: WaitListInterface[];
  offeringConsentTypes?: OfferingConsentTypeInterface[];
  responsibilities?: ResponsibilityInterface[];
  fundingDepartments?: DepartmentInterface[];
  courseNbrRegex?: string;
  courseNbrInfo?: string;
  courseOfferingMustBeUnique?: boolean;
  courseOfferingNumberUpperCase?: boolean;
  allowAlternativeCourseOfferings?: boolean;
  coursesFundingDepartmentsEnabled?: boolean;
  canEditExternalIds?: boolean;
  canShowExternalIds?: boolean;
  waitListDefault?: number;
  wkEnrollDefault?: number;
  wkChangeDefault?: number;
  wkDropDefault?: number;
  weekStartDayOfWeek?: string;
  prefRowsAdded?: number;
  courseUrlProvider?: string;
  instructionalOfferingId?: string;
  defaultTeachingResponsibilityId?: string;
  subjectAreaEffectiveFundingDept?: number;
  instructors?: InstructorInterface[];
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingPropertiesRequest */
export interface CourseOfferingPropertiesRequest {
  sessionId?: number;
  isEdit?: boolean;
  subjAreaId?: number;
  courseOfferingId?: number;
  courseNumber?: string;
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface */
export interface CourseRequestInterface extends StudentSectioningContext {
  courses?: CourseRequestInterface_Request[];
  alternatives?: CourseRequestInterface_Request[];
  saved?: boolean;
  noChange?: boolean;
  allowTimeConf?: boolean;
  allowRoomConf?: boolean;
  linkedConf?: boolean;
  deadlineConf?: boolean;
  updateLastRequest?: boolean;
  lastCourse?: RequestedCourse;
  confirmations?: CourseMessage[];
  maxCredit?: number;
  maxCreditOverride?: number;
  maxCreditOverrideStatus?: RequestedCourseStatus;
  maxCreditOverrideExternalId?: string;
  maxCreditOverrideTimeStamp?: string;
  creditWarning?: string;
  creditNote?: string;
  errorMessage?: string;
  specRegDashboardUrl?: string;
  requestorNote?: string;
  requestorNoteSuggestions?: string[];
  requestId?: string;
  popupMessage?: string;
  pinReleased?: boolean;
  mode?: WaitListMode;
  waitListChecks?: CheckCoursesResponse;
  changedBy?: string;
  timeStamp?: string;
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.Filter */
export interface CourseRequestInterface_Filter {
  classFrom?: string;
  classTo?: string;
  daysFrom?: number;
  daysTo?: number;
  creditMin?: number;
  creditMax?: number;
  instructor?: string;
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.Preference */
export interface CourseRequestInterface_Preference {
  id?: number;
  text?: string;
  required?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.Request */
export interface CourseRequestInterface_Request {
  requestedCourse?: RequestedCourse[];
  waitList?: boolean;
  noSub?: boolean;
  critical?: number;
  timeStamp?: string;
  waitListedTimeStamp?: string;
  filter?: string;
  advisorCredit?: string;
  advisorNote?: string;
  waitListSwapWithCourseOfferingId?: number;
  changedBy?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.CourseRequirement */
export interface CourseRequirement extends Course {
  externalId?: string;
  instructorId?: number;
  instructorName?: string;
  note?: string;
  time?: string;
  room?: string;
  dist?: string;
  timeHtml?: string;
  roomHtml?: string;
  distHtml?: string;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseTypeInterface */
export interface CourseTypeInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Curriculum */
export interface Curriculum extends IdName {
  classifications?: IdName[];
  majors?: IdName[];
  area?: IdName;
  limit?: number;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.CurriculumClassificationInterface */
export interface CurriculumClassificationInterface {
  curriculumId?: number;
  clasfId?: number;
  name?: string;
  nrStudents?: number;
  enrollment?: number;
  lastLike?: number;
  projection?: number;
  requested?: number;
  snapshotProjection?: number;
  snapshotNrStudents?: number;
  clasf?: AcademicClassificationInterface;
  courses?: CurriculumCourseInterface[];
  sessionHasSnapshotData?: boolean;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.CurriculumCourseGroupInterface */
export interface CurriculumCourseGroupInterface {
  id?: number;
  name?: string;
  color?: string;
  type?: number;
  editable?: boolean;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.CurriculumCourseInterface */
export interface CurriculumCourseInterface {
  id?: number;
  courseId?: number;
  clasfId?: number;
  courseName?: string;
  share?: number;
  defaultShare?: number;
  snapshotShare?: number;
  defaultSnapshotShare?: number;
  lastLike?: number;
  enrollment?: number;
  projection?: number;
  requested?: number;
  snapshotProjection?: number;
  templates?: string[];
  sessionHasSnapshotData?: boolean;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.CurriculumFilterRpcRequest */
export interface CurriculumFilterRpcRequest extends FilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.CurriculumInfo */
export interface CurriculumInfo {
  name?: string;
  nrStudents?: number;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface */
export interface CurriculumInterface {
  id?: number;
  abbv?: string;
  name?: string;
  editable?: boolean;
  lastChange?: string;
  multipleMajors?: boolean;
  sessionHasSnapshotData?: boolean;
  academicArea?: AcademicAreaInterface;
  majors?: MajorInterface[];
  dept?: CurriculumInterface_DepartmentInterface;
  clasf?: CurriculumClassificationInterface[];
  courses?: CourseInterface[];
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.DepartmentInterface */
export interface CurriculumInterface_DepartmentInterface {
  deptId?: number;
  deptCode?: string;
  deptAbbv?: string;
  deptName?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.CustomField */
export interface CustomField {
  id?: number;
  name?: string;
  length?: number;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.DateInfo */
export interface DateInfo {
  datePatternName?: string;
  datePatternPref?: number;
  datePatternId?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.DateInterface */
export interface DateInterface {
  label?: string;
  month?: number;
  day?: number;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.DefaultExpirationDates */
export interface DefaultExpirationDates {
  expirations?: { [key: string]: string };
  startDates?: { [key: string]: string };
  inclusive?: boolean;
}

/** org.unitime.timetable.gwt.shared.DegreePlanInterface.DegreeCourseInterface */
export interface DegreeCourseInterface extends DegreeMultiSelectionInterface {
  courseId?: number;
  subject?: string;
  course?: string;
  title?: string;
  selected?: boolean;
  courses?: CourseAssignment[];
  critical?: boolean;
}

/** org.unitime.timetable.gwt.shared.DegreePlanInterface.DegreeGroupInterface */
export interface DegreeGroupInterface extends DegreeMultiSelectionInterface {
  choice?: boolean;
  placeHolder?: boolean;
  courses?: DegreeCourseInterface[];
  groups?: DegreeGroupInterface[];
  placeHolders?: DegreePlaceHolderInterface[];
  selected?: boolean;
  description?: string;
  critical?: boolean;
}

/** org.unitime.timetable.gwt.shared.DegreePlanInterface.DegreeItemInterface */
export interface DegreeItemInterface {
  id?: string;
  selection?: string[];
}

/** org.unitime.timetable.gwt.shared.DegreePlanInterface.DegreeMultiSelectionInterface */
export interface DegreeMultiSelectionInterface extends DegreeItemInterface {
  selection?: string[];
}

/** org.unitime.timetable.gwt.shared.DegreePlanInterface.DegreePlaceHolderInterface */
export interface DegreePlaceHolderInterface extends DegreeItemInterface {
  type?: string;
  name?: string;
}

/** org.unitime.timetable.gwt.shared.DegreePlanInterface */
export interface DegreePlanInterface {
  studentId?: number;
  sessionId?: number;
  id?: string;
  name?: string;
  degree?: string;
  school?: string;
  track?: string;
  modifiedWho?: string;
  modified?: string;
  group?: DegreeGroupInterface;
  locked?: boolean;
  active?: boolean;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.DeleteRecordRpcRequest */
export interface DeleteRecordRpcRequest extends SaveRecordRpcRequest {
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.DeleteScriptRpcRequest */
export interface DeleteScriptRpcRequest {
  id?: number;
  name?: string;
}

/** org.unitime.timetable.gwt.shared.TaskInterface.DeleteTaskDetailsRpcRequest */
export interface DeleteTaskDetailsRpcRequest {
  taskId?: number;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.DepartmentInterface */
export interface DepartmentInterface {
  id?: number;
  abbv?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface */
export interface DepartmentInterface2 {
  uniqueId?: number;
  sessionId?: number;
  academicSessionName?: string;
  name?: string;
  deptCode?: string;
  statusType?: string;
  statusTypeCode?: string;
  abbreviation?: string;
  externalId?: string;
  distributionPrefPriority?: number;
  externalMgrLabel?: string;
  externalMgrAbbv?: string;
  timetableManagerCount?: number;
  lastChangeStr?: string;
  externalManager?: boolean;
  allowEvents?: boolean;
  allowStudentScheduling?: boolean;
  inheritInstructorPreferences?: boolean;
  allowReqTime?: boolean;
  allowReqRoom?: boolean;
  allowReqDistribution?: boolean;
  externalFundingDept?: boolean;
  externalStatusTypesStr?: string;
  dependentStatusesStr?: string[];
  subjectAreaCount?: number;
  roomDeptsCount?: number;
  dependentStatuses?: string[];
  dependentDepartments?: string[];
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface.DepartmentOption */
export interface DepartmentOption {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface.DepartmentPropertiesInterface */
export interface DepartmentPropertiesInterface {
  canExportPdf?: boolean;
  coursesFundingDepartmentsEnabled?: boolean;
  academicSessionName?: string;
  statuses?: StatusOption[];
  extDepartments?: DepartmentOption[];
  canEdit?: boolean;
  canDelete?: boolean;
  canChangeExtManager?: boolean;
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface.DepartmentPropertiesRequest */
export interface DepartmentPropertiesRequest {
  departmentId?: number;
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface.DepartmentsDataResponse */
export interface DepartmentsDataResponse {
  departments?: DepartmentInterface2[];
  canAdd?: boolean;
  fundingDeptEnabled?: boolean;
  canExportPdf?: boolean;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.DistributionInfo */
export interface DistributionInfo {
  info?: GroupConstraintInfo;
  other?: ClassAssignmentDetails[];
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.EligibilityCheck */
export interface EligibilityCheck {
  flags?: number;
  message?: string;
  checkboxMessage?: string;
  sessionId?: number;
  studentId?: number;
  overrides?: string[];
  overrideRequestDisclaimer?: string;
  gradeModes?: GradeModes;
  maxCredit?: number;
  advisorWaitListedCourseIds?: number[];
}

/** org.unitime.timetable.gwt.shared.EventInterface.EncodeQueryRpcRequest */
export interface EncodeQueryRpcRequest {
  query?: string;
  hash?: boolean;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EncodeQueryRpcResponse */
export interface EncodeQueryRpcResponse {
  query?: string;
  hash?: string;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.Enrollment */
export interface Enrollment {
  student?: Student;
  course?: CourseAssignment;
  priority?: number;
  alternative?: string;
  requestedDate?: string;
  enrolledDate?: string;
  approvedDate?: string;
  waitListedDate?: string;
  reservation?: string;
  approvedBy?: string;
  conflicts?: Conflict[];
  waitList?: boolean;
  noSub?: boolean;
  enrollmentMessage?: string;
  waitListedPosition?: string;
  waitListReplacement?: string;
  critical?: number;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.EnrollmentInfo */
export interface EnrollmentInfo {
  area?: string;
  major?: string;
  classification?: string;
  subject?: string;
  courseNbr?: string;
  config?: string;
  subpart?: string;
  clazz?: string;
  title?: string;
  consent?: string;
  courseId?: number;
  offeringId?: number;
  subjectId?: number;
  configId?: number;
  subpartId?: number;
  clazzId?: number;
  limit?: number;
  other?: number;
  projection?: number;
  enrollment?: number;
  waitlist?: number;
  reservation?: number;
  available?: number;
  unassigned?: number;
  unassignedPrimary?: number;
  snapshot?: number;
  noSub?: number;
  totalEnrollment?: number;
  totalWaitlist?: number;
  totalReservation?: number;
  totalUnassigned?: number;
  totalUnassignedPrimary?: number;
  totalNoSub?: number;
  swap?: number;
  totalSwap?: number;
  consentNeeded?: number;
  totalConsentNeeded?: number;
  overrideNeeded?: number;
  totalOverrideNeeded?: number;
  assignment?: ClassAssignment;
  level?: number;
  control?: boolean;
  masterCourseId?: number;
  masterSubject?: string;
  masterCourseNbr?: string;
  noMatch?: boolean;
}

/** org.unitime.timetable.gwt.shared.EventInterface.FilterRpcResponse.Entity */
export interface Entity {
  uniqueId?: number;
  abbv?: string;
  name?: string;
  count?: number;
  params?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.ErrorMessage */
export interface ErrorMessage {
  course?: string;
  section?: string;
  code?: string;
  message?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventDetailRpcRequest */
export interface EventDetailRpcRequest extends EventRpcRequest {
  eventId?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventEnrollmentsRpcRequest */
export interface EventEnrollmentsRpcRequest extends EventRpcRequest {
  relatedObjects?: RelatedObjectInterface[];
  meetings?: MeetingInterface[];
  eventId?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventFilterRpcRequest */
export interface EventFilterRpcRequest extends FilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.EventInterface */
export interface EventInterface {
  eventId?: number;
  eventName?: string;
  eventType?: EventType;
  eventEmail?: string;
  meetings?: MeetingInterface[];
  contact?: ContactInterface;
  sponsor?: SponsoringOrganizationInterface;
  instructors?: ContactInterface[];
  coordinators?: ContactInterface[];
  additionalContacts?: ContactInterface[];
  lastChange?: string;
  notes?: NoteInterface[];
  expirationDate?: string;
  courseIds?: number[];
  courseNames?: string[];
  courseTitles?: string[];
  instruction?: string;
  instructionType?: number;
  maxCapacity?: number;
  enrollment?: number;
  reqAttendance?: boolean;
  externalIds?: string[];
  sectionNumber?: string;
  canView?: boolean;
  canEdit?: boolean;
  relatedObjects?: RelatedObjectInterface[];
  conflicts?: EventInterface[];
  message?: string;
  timeStamp?: string;
  sequence?: number;
  requestedServices?: EventServiceProviderInterface[];
  classId?: number;
  sessionId?: number;
  deptCode?: string;
  session?: SessionInterface;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventLookupRpcRequest */
export interface EventLookupRpcRequest extends EventRpcRequest {
  resourceType?: ResourceType;
  resourceId?: number;
  resourceExternalId?: string;
  eventFilter?: EventFilterRpcRequest;
  roomFilter?: RoomFilterRpcRequest;
  limit?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventPropertiesRpcRequest */
export interface EventPropertiesRpcRequest extends EventRpcRequest {
  pageName?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventPropertiesRpcResponse */
export interface EventPropertiesRpcResponse {
  canLookupPeople?: boolean;
  canLookupMainContact?: boolean;
  canLookupAdditionalContacts?: boolean;
  canAddEvent?: boolean;
  canAddCourseEvent?: boolean;
  canAddUnavailableEvent?: boolean;
  canExportCSV?: boolean;
  canSetExpirationDate?: boolean;
  sponsoringOrganizations?: SponsoringOrganizationInterface[];
  mainContact?: ContactInterface;
  standardNotes?: StandardEventNoteInterface[];
  emailConfirmation?: boolean;
  canSaveFilterDefaults?: boolean;
  filterDefaults?: { [key: string]: string };
  tooEarlySlot?: number;
  canEditAcademicTitle?: boolean;
  gridDisplayTitle?: boolean;
  student?: boolean;
  viewMeetingContacts?: boolean;
  editMeetingContacts?: boolean;
  eventServiceProviders?: EventServiceProviderInterface[];
  firstDayOfWeek?: number;
  courseEventDefaultStudentAttendance?: boolean;
  expectedAttendanceRequired?: boolean;
  canEmailStudents?: boolean;
  sponsoringOrgRequired?: boolean;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventRoomAvailabilityRpcRequest */
export interface EventRoomAvailabilityRpcRequest extends EventRpcRequest {
  eventId?: number;
  eventType?: EventType;
  startSlot?: number;
  endSlot?: number;
  dates?: number[];
  locations?: number[];
  meetings?: MeetingInterface[];
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventRoomAvailabilityRpcResponse */
export interface EventRoomAvailabilityRpcResponse {
  overlaps?: { [key: string]: { [key: string]: MeetingConflictInterface[] } };
  meetings?: MeetingInterface[];
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventRpcRequest */
export interface EventRpcRequest {
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.EventServiceProviderInterface */
export interface EventServiceProviderInterface {
  id?: number;
  reference?: string;
  label?: string;
  message?: string;
  email?: string;
  departmentId?: number;
  locationIds?: number[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.ExamTypeInterface */
export interface ExamTypeInterface {
  id?: number;
  reference?: string;
  label?: string;
  final?: boolean;
}

/** org.unitime.timetable.gwt.client.sectioning.ExaminationEnrollmentTable.ExaminationEnrollmentsRpcRequest */
export interface ExaminationEnrollmentsRpcRequest {
  examId?: number;
}

/** org.unitime.timetable.gwt.client.sectioning.ExaminationEnrollmentTable.ExaminationScheduleRpcRequest */
export interface ExaminationScheduleRpcRequest {
  examId?: number;
  studentId?: number;
}

/** org.unitime.timetable.gwt.client.sectioning.ExaminationEnrollmentTable.ExaminationScheduleRpcResponse */
export interface ExaminationScheduleRpcResponse {
  examType?: string;
  exams?: RelatedObjectInterface[];
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.ExecuteScriptRpcRequest */
export interface ExecuteScriptRpcRequest {
  id?: number;
  name?: string;
  parameters?: { [key: string]: string };
  email?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.FeatureInterface */
export interface FeatureInterface extends RoomPropertyInterface {
  department?: RoomInterface_DepartmentInterface;
  type?: FeatureTypeInterface;
  rooms?: Entity[];
  canEdit?: boolean;
  canDelete?: boolean;
  sessionId?: number;
  sessionName?: string;
  description?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.FeatureTypeInterface */
export interface FeatureTypeInterface {
  id?: number;
  abbv?: string;
  label?: string;
  events?: boolean;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.Field */
export interface Field {
  name?: string;
  type?: FieldType;
  length?: number;
  width?: number;
  height?: number;
  flags?: number;
  values?: ListItem[];
}

/** org.unitime.timetable.gwt.client.widgets.UniTimeFileUpload.FileUploadRpcRequest */
export interface FileUploadRpcRequest {
  reset?: boolean;
}

/** org.unitime.timetable.gwt.client.widgets.UniTimeFileUpload.FileUploadRpcResponse */
export interface FileUploadRpcResponse {
  name?: string;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.Filter */
export interface Filter {
  fields?: SimpleEditInterface_Field[];
  defaultValue?: SimpleEditInterface_Record;
}

/** org.unitime.timetable.gwt.shared.FilterInterface */
export interface FilterInterface {
  parameters?: FilterParameterInterface[];
}

/** org.unitime.timetable.gwt.shared.FilterInterface.ListItem */
export interface FilterInterface_ListItem {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.FilterInterface.FilterParameterInterface */
export interface FilterParameterInterface {
  name?: string;
  label?: string;
  type?: string;
  value?: string;
  default?: string;
  suffix?: string;
  options?: FilterInterface_ListItem[];
  multiSelect?: boolean;
  collapsible?: boolean;
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.FilterRpcRequest */
export interface FilterRpcRequest extends EventRpcRequest {
  command?: Command;
  text?: string;
  options?: { [key: string]: string[] };
}

/** org.unitime.timetable.gwt.shared.EventInterface.FilterRpcResponse */
export interface FilterRpcResponse {
  entities?: { [key: string]: Entity[] };
  typeLabels?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.Flag */
export interface Flag {
  value?: number;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.FreeTime */
export interface FreeTime {
  days?: number[];
  start?: number;
  length?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.FutureRoomInterface */
export interface FutureRoomInterface {
  id?: number;
  label?: string;
  displayName?: string;
  capacity?: number;
  type?: string;
  externalId?: string;
  session?: RoomInterface_AcademicSessionInterface;
  canChange?: boolean;
  canDelete?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.GeocodeRequest */
export interface GeocodeRequest {
  reverse?: boolean;
  lat?: number;
  lon?: number;
  viewbox?: string;
  query?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.GeocodeResponse */
export interface GeocodeResponse {
  lat?: number;
  lon?: number;
  query?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.GetBuildingsRequest */
export interface GetBuildingsRequest {
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.GetCourseOfferingRequest */
export interface GetCourseOfferingRequest {
  courseOfferingId?: number;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.GetCourseOfferingResponse */
export interface GetCourseOfferingResponse {
  courseOffering?: CourseOfferingInterface;
  wkEnrollDefault?: number;
  wkChangeDefault?: number;
  wkDropDefault?: number;
  weekStartDayOfWeek?: string;
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface.GetDepartmentsRequest */
export interface GetDepartmentsRequest {
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.GetFilterRpcRequest */
export interface GetFilterRpcRequest extends SimpleEditInterface_SimpleEditRpcRequest {
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.GetInstructorAttributeParentsRequest */
export interface GetInstructorAttributeParentsRequest {
  departmentId?: number;
  typeId?: number;
  attributeId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.GetInstructorAttributesRequest */
export interface GetInstructorAttributesRequest {
  departmentId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.GetInstructorsRequest */
export interface GetInstructorsRequest {
  departmentId?: number;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.GetPageNameRpcRequest */
export interface GetPageNameRpcRequest extends SimpleEditRpcRequest {
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.GetQueueTableRpcRequest */
export interface GetQueueTableRpcRequest {
  deleteId?: string;
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.GetRequestsRpcRequest */
export interface GetRequestsRpcRequest {
  offeringId?: number;
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.GetRequestsRpcResponse */
export interface GetRequestsRpcResponse extends Properties {
  requests?: Request[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.GetRoomsOfABuildingRequest */
export interface GetRoomsOfABuildingRequest {
  buildingId?: number;
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.GetScriptOptionsRpcRequest */
export interface GetScriptOptionsRpcRequest {
}

/** org.unitime.timetable.gwt.shared.TaskInterface.GetTaskExecutionLogRpcRequest */
export interface GetTaskExecutionLogRpcRequest {
  taskExecutionId?: number;
}

/** org.unitime.timetable.gwt.shared.TaskInterface.GetTaskOptionsRpcRequest */
export interface GetTaskOptionsRpcRequest {
}

/** org.unitime.timetable.gwt.shared.TaskInterface.GetTasksRpcRequest */
export interface GetTasksRpcRequest {
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.GradeMode */
export interface GradeMode {
  code?: string;
  label?: string;
  honor?: boolean;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.GradeModes */
export interface GradeModes {
  modes?: { [key: string]: GradeMode };
  creditHours?: { [key: string]: number };
  currentCredit?: number;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.Group */
export interface Group {
  type?: string;
  name?: string;
  title?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.GroupConstraintInfo */
export interface GroupConstraintInfo {
  preference?: string;
  isSatisfied?: boolean;
  name?: string;
  type?: string;
  value?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.GroupInterface */
export interface GroupInterface extends RoomPropertyInterface {
  department?: RoomInterface_DepartmentInterface;
  default?: boolean;
  description?: string;
  rooms?: Entity[];
  canEdit?: boolean;
  canDelete?: boolean;
  sessionId?: number;
  sessionName?: string;
}

/** org.unitime.timetable.gwt.command.client.GwtRpcResponseBoolean */
export interface GwtRpcResponseBoolean {
  value?: boolean;
}

/** org.unitime.timetable.gwt.command.client.GwtRpcResponseLong */
export interface GwtRpcResponseLong {
  value?: number;
}

/** org.unitime.timetable.gwt.command.client.GwtRpcResponseNull */
export interface GwtRpcResponseNull {
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLDeleteRpcRequest */
export interface HQLDeleteRpcRequest {
  id?: number;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLExecuteRpcRequest */
export interface HQLExecuteRpcRequest {
  query?: Query;
  options?: SavedHQLInterface_IdValue[];
  fromRow?: number;
  maxRows?: number;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLOptionsInterface */
export interface HQLOptionsInterface {
  flags?: SavedHQLInterface_Flag[];
  options?: Option[];
  editable?: boolean;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLOptionsRpcRequest */
export interface HQLOptionsRpcRequest {
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLQueriesRpcRequest */
export interface HQLQueriesRpcRequest {
  appearance?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLSetBackRpcRequest */
export interface HQLSetBackRpcRequest {
  appearance?: string;
  history?: string;
  ids?: number[];
  type?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.HQLStoreRpcRequest */
export interface HQLStoreRpcRequest extends Query {
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.IdLabel */
export interface IdLabel {
  id?: number;
  label?: string;
  description?: string;
  allowedPrefs?: number[];
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.IdName */
export interface IdName {
  id?: number;
  abbv?: string;
  name?: string;
  limit?: number;
  parentId?: number;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.IdValue */
export interface IdValue {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.InfoInterface */
export interface InfoInterface {
  pairs?: InfoPairInterface[];
}

/** org.unitime.timetable.gwt.shared.SolverInterface.InfoPair */
export interface InfoPair {
  name?: string;
  value?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.InfoPairInterface */
export interface InfoPairInterface {
  name?: string;
  value?: string;
  separator?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstrOfferingConfigInterface */
export interface InstrOfferingConfigInterface {
  subpartLines?: SubpartLine[];
  departments?: InstrOfferingConfigInterface_Reference[];
  instructionalMethods?: InstrOfferingConfigInterface_Reference[];
  durationTypes?: InstrOfferingConfigInterface_Reference[];
  instructionalTypes?: InstrOfferingConfigInterface_Reference[];
  configs?: InstrOfferingConfigInterface_Reference[];
  offeringId?: number;
  courseId?: number;
  configId?: number;
  operation?: InstrOfferingConfigInterface_Operation;
  limit?: number;
  unlimited?: boolean;
  displayOptionForMaxLimit?: boolean;
  displayMaxLimit?: boolean;
  canDelete?: boolean;
  instructionalMethodId?: number;
  instructionalMethodEditable?: boolean;
  durationTypeId?: number;
  durationTypeEditable?: boolean;
  courseName?: string;
  configName?: string;
  lastGeneratedId?: number;
  displayCourseLink?: boolean;
  checkLimits?: boolean;
  maxNumberOfClasses?: number;
  op?: string;
}

/** org.unitime.timetable.gwt.shared.InstrOfferingConfigInterface.Reference */
export interface InstrOfferingConfigInterface_Reference {
  id?: number;
  reference?: string;
  label?: string;
  selectable?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.InstructorAssignmentRequest */
export interface InstructorAssignmentRequest {
  assignments?: AssignmentInfo[];
  ignoreConflicts?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.InstructorAttributePropertiesInterface */
export interface InstructorAttributePropertiesInterface {
  departments?: InstructorInterface_DepartmentInterface[];
  attributeTypes?: AttributeTypeInterface[];
  canAddGlobalAttribute?: boolean;
  lastDepartmentId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.InstructorAttributePropertiesRequest */
export interface InstructorAttributePropertiesRequest {
}

/** org.unitime.timetable.gwt.client.instructor.InstructorAvailabilityWidget.InstructorAvailabilityModel */
export interface InstructorAvailabilityModel extends RoomSharingModel {
}

/** org.unitime.timetable.gwt.client.instructor.InstructorAvailabilityWidget.InstructorAvailabilityRequest */
export interface InstructorAvailabilityRequest {
  instructorId?: string;
  notAvailable?: boolean;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorDepartment */
export interface InstructorDepartment {
  id?: number;
  label?: string;
  deptCode?: string;
  position?: IdLabel;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.InstructorInfo */
export interface InstructorInfo {
  instructorId?: number;
  externalId?: string;
  name?: string;
  assignedLoad?: number;
  maxLoad?: number;
  teachingPreference?: string;
  timePreferences?: PreferenceInfo[];
  coursePreferences?: PreferenceInfo[];
  distributionPreferences?: PreferenceInfo[];
  attributes?: AttributeInterface[];
  values?: { [key: string]: number };
  availability?: string;
  assignedRequests?: TeachingRequestInfo[];
  enrollments?: ClassInfo[];
  assignmentIndex?: number;
  conflict?: boolean;
  matching?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.InstructorInterface */
export interface InstructorInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface */
export interface InstructorInterface2 {
  id?: number;
  externalId?: string;
  firstName?: string;
  middleName?: string;
  lastName?: string;
  formattedName?: string;
  orderName?: string;
  department?: InstructorInterface_DepartmentInterface;
  position?: PositionInterface;
  teachingPreference?: InstructorInterface_PreferenceInterface;
  maxLoad?: number;
  attributes?: AttributeInterface[];
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.DepartmentInterface */
export interface InstructorInterface_DepartmentInterface {
  id?: number;
  abbv?: string;
  code?: string;
  label?: string;
  title?: string;
  canAddAttribute?: boolean;
  canSeeAttributes?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.PreferenceInterface */
export interface InstructorInterface_PreferenceInterface {
  code?: string;
  name?: string;
  abbv?: string;
  color?: string;
  id?: number;
  editable?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.SubjectAreaInterface */
export interface InstructorInterface_SubjectAreaInterface {
  id?: number;
  abbv?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorRequirementData */
export interface InstructorRequirementData {
  customFields?: CustomField[];
  instructorRequirements?: CourseRequirement[];
  crossList?: boolean;
  admin?: boolean;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorRequirementsRequest */
export interface InstructorRequirementsRequest {
  offeringId?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorSurveyApplyRequest */
export interface InstructorSurveyApplyRequest {
  instructorId?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorSurveyCopyRequest */
export interface InstructorSurveyCopyRequest {
  data?: InstructorSurveyData;
  preferencesSessionId?: number;
  coursesSessionId?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorSurveyData */
export interface InstructorSurveyData {
  id?: number;
  sessionId?: number;
  externalId?: string;
  formattedName?: string;
  email?: string;
  note?: string;
  changedBy?: string;
  appliedDept?: string;
  submitted?: string;
  applied?: string;
  changed?: string;
  departments?: InstructorDepartment[];
  timePrefs?: InstructorTimePreferencesModel;
  roomPrefs?: Preferences[];
  distPrefs?: Preferences;
  prefLevels?: PrefLevel[];
  courses?: Course[];
  customFields?: CustomField[];
  editable?: boolean;
  canApply?: boolean;
  admin?: boolean;
  canDelete?: boolean;
  sessions?: AcademicSessionInfo[];
  sessionsWithPreferences?: AcademicSessionInfo[];
  sessionsWithCourses?: AcademicSessionInfo[];
  popupMessage?: string;
  popupWarning?: boolean;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorSurveyDeleteRequest */
export interface InstructorSurveyDeleteRequest {
  instructorId?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorSurveyRequest */
export interface InstructorSurveyRequest {
  externalId?: string;
  instructorId?: number;
  session?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorSurveySaveRequest */
export interface InstructorSurveySaveRequest {
  data?: InstructorSurveyData;
  instructorId?: number;
  submit?: boolean;
  unsubmit?: boolean;
  changed?: boolean;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.InstructorTimePreferencesModel */
export interface InstructorTimePreferencesModel extends InstructorAvailabilityModel {
  problem?: Problem;
  instructorPattern?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.IsSessionBusyRpcRequest */
export interface IsSessionBusyRpcRequest {
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.JenrlInfo */
export interface JenrlInfo {
  jenrl?: number;
  isSatisfied?: boolean;
  isHard?: boolean;
  isDistance?: boolean;
  isFixed?: boolean;
  isCommited?: boolean;
  isImportant?: boolean;
  isInstructor?: boolean;
  isWorkDay?: boolean;
  distance?: number;
  curriculum2nrStudents?: CurriculumInfo[];
}

/** org.unitime.timetable.gwt.shared.LastChangesInterface.LastChangesRequest */
export interface LastChangesRequest {
  objectType?: string;
  objectId?: number;
  options?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.ListAcademicClassifications */
export interface ListAcademicClassifications {
  sessionId?: number;
}

/** org.unitime.timetable.gwt.client.events.AcademicSessionSelectionBox.ListAcademicSessions */
export interface ListAcademicSessions {
  term?: string;
  source?: string;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.ListClasses */
export interface ListClasses {
  sessionId?: number;
  course?: string;
  courseId?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.ListCourseOfferings */
export interface ListCourseOfferings {
  sessionId?: number;
  query?: string;
  limit?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.ListCurricula */
export interface ListCurricula {
  sessionId?: number;
  course?: string;
  courseId?: number;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.ListItem */
export interface ListItem {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.ListSolutionsRequest */
export interface ListSolutionsRequest {
  operation?: SolutionOperation;
  solutionIds?: number[];
  configurationId?: number;
  ownerId?: number;
  host?: string;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.ListSolutionsResponse */
export interface ListSolutionsResponse extends TableInterface {
  loadDate?: string;
  pageMessages?: PageMessage[];
  currentSolution?: SolutionInfo;
  log?: ProgressMessage[];
  selectedSolutions?: SolutionInfo[];
  host?: string;
  hosts?: string[];
  solverStatus?: string;
  solverProgress?: string;
  configurationId?: number;
  configurations?: SolverConfiguration[];
  working?: boolean;
  operation?: SolutionOperation;
  operations?: { [key: string]: number };
  message?: string;
  solverOwners?: SolverOwner[];
  ownerIds?: number[];
  errors?: string[];
  success?: boolean;
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.LoadAllScriptsRpcRequest */
export interface LoadAllScriptsRpcRequest {
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.LoadDataRpcRequest */
export interface LoadDataRpcRequest extends SimpleEditRpcRequest {
  configIdStr?: string;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.LoadRecordRpcRequest */
export interface LoadRecordRpcRequest extends SimpleEditInterface_SimpleEditRpcRequest {
  record?: SimpleEditInterface_Record;
}

/** org.unitime.timetable.gwt.shared.PersonInterface.LookupRequest */
export interface LookupRequest {
  query?: string;
  options?: string;
}

/** org.unitime.timetable.gwt.shared.CurriculumInterface.MajorInterface */
export interface MajorInterface {
  majorId?: number;
  majorCode?: string;
  majorName?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.MakeAssignmentRequest */
export interface MakeAssignmentRequest {
  assignments?: SelectedAssignment[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.MapPropertiesInterface */
export interface MapPropertiesInterface {
  googleMap?: boolean;
  leafletMap?: boolean;
  googleMapApiKey?: string;
  leafletMapTiles?: string;
  leafletMapAttribution?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.MapPropertiesRequest */
export interface MapPropertiesRequest {
}

/** org.unitime.timetable.gwt.shared.EventInterface.MeetingConflictInterface */
export interface MeetingConflictInterface extends MeetingInterface {
  eventId?: number;
  eventName?: string;
  eventType?: EventType;
  limit?: number;
  enrollment?: number;
  sponsor?: SponsoringOrganizationInterface;
  instructors?: ContactInterface[];
}

/** org.unitime.timetable.gwt.shared.EventInterface.MeetingInterface */
export interface MeetingInterface {
  location?: ResourceInterface;
  meetingId?: number;
  meetingDate?: string;
  startSlot?: number;
  endSlot?: number;
  startOffset?: number;
  endOffset?: number;
  dayOfWeek?: number;
  dayOfYear?: number;
  past?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  canCancel?: boolean;
  canApprove?: boolean;
  canInquire?: boolean;
  automaticApproval?: boolean;
  gridIndex?: number;
  approvalDate?: string;
  approvalStatus?: ApprovalStatus;
  startTime?: number;
  stopTime?: number;
  conflicts?: MeetingConflictInterface[];
  meetingContacts?: ContactInterface[];
  style?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface */
export interface MenuInterface {
  name?: string;
  title?: string;
  page?: string;
  hash?: string;
  parameters?: { [key: string]: string[] };
  target?: string;
  gWT?: boolean;
  subMenus?: MenuInterface[];
}

/** org.unitime.timetable.gwt.shared.MenuInterface.MenuRpcRequest */
export interface MenuRpcRequest {
}

/** org.unitime.timetable.gwt.shared.EventInterface.MessageInterface */
export interface MessageInterface {
  level?: MessageInterface_Level;
  message?: string;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.NotAssignedClassesFilterRequest */
export interface NotAssignedClassesFilterRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.NotAssignedClassesFilterResponse */
export interface NotAssignedClassesFilterResponse extends AssignedClassesFilterResponse {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.NotAssignedClassesRequest */
export interface NotAssignedClassesRequest {
  filter?: FilterInterface;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.NotAssignedClassesResponse */
export interface NotAssignedClassesResponse extends AssignedClassesResponse {
  showNote?: boolean;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.Note */
export interface Note {
  id?: number;
  timeStamp?: string;
  message?: string;
  owner?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.NoteInterface */
export interface NoteInterface {
  id?: number;
  date?: string;
  user?: string;
  type?: NoteType;
  meetings?: string;
  note?: string;
  attachment?: string;
  link?: string;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Offering */
export interface Offering extends IdName {
  offered?: boolean;
  needUnlock?: boolean;
  courses?: ReservationInterface_Course[];
  configs?: Config[];
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.OfferingConsentTypeInterface */
export interface OfferingConsentTypeInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.Option */
export interface Option {
  type?: string;
  name?: string;
  values?: SavedHQLInterface_IdValue[];
  multiSelect?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.OverrideTypeInterface */
export interface OverrideTypeInterface {
  id?: number;
  reference?: string;
  name?: string;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDDeleteRpcRequest */
export interface PITDDeleteRpcRequest {
  id?: number;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDExecuteRpcRequest */
export interface PITDExecuteRpcRequest {
  report?: Report;
  parameters?: IdValue[];
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDParametersInterface */
export interface PITDParametersInterface {
  flags?: Flag[];
  parameters?: Parameter[];
  editable?: boolean;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDParametersRpcRequest */
export interface PITDParametersRpcRequest {
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDQueriesRpcRequest */
export interface PITDQueriesRpcRequest {
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDSetBackRpcRequest */
export interface PITDSetBackRpcRequest {
  history?: string;
  ids?: number[];
  type?: string;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.PITDStoreRpcRequest */
export interface PITDStoreRpcRequest extends Report {
}

/** org.unitime.timetable.gwt.shared.SolverInterface.PageMessage */
export interface PageMessage {
  type?: PageMessageType;
  message?: string;
  url?: string;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.PageName */
export interface PageName {
  singular?: string;
  plural?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.PageNameInterface */
export interface PageNameInterface {
  helpUrl?: string;
  name?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.PageNameRpcRequest */
export interface PageNameRpcRequest {
  name?: string;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.Parameter */
export interface Parameter {
  type?: string;
  name?: string;
  values?: IdValue[];
  multiSelect?: boolean;
  textField?: boolean;
  defaultTextValue?: string;
}

/** org.unitime.timetable.gwt.client.admin.PasswordPage.PasswordChangeRequest */
export interface PasswordChangeRequest {
  username?: string;
  email?: string;
  oldPassword?: string;
  newPassword?: string;
  reset?: boolean;
}

/** org.unitime.timetable.gwt.client.admin.PasswordPage.PasswordChangeResponse */
export interface PasswordChangeResponse {
}

/** org.unitime.timetable.gwt.shared.RoomInterface.PeriodInterface */
export interface PeriodInterface {
  id?: number;
  day?: number;
  start?: number;
  length?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.PeriodPreferenceModel */
export interface PeriodPreferenceModel {
  examType?: ExamTypeInterface;
  locationId?: number;
  examId?: number;
  defaultPreference?: number;
  selectedPreference?: number;
  firstDate?: string;
  starts?: number[];
  days?: number[];
  preferences?: PreferenceInterface[];
  periods?: PeriodInterface[];
  model?: { [key: string]: { [key: string]: number } };
  horizontal?: boolean;
  assignedPeriodId?: number;
  reqConfirmation?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.PeriodPreferenceRequest */
export interface PeriodPreferenceRequest {
  operation?: PeriodPreferenceRequest_Operation;
  sourceId?: number;
  model?: PeriodPreferenceModel;
  examTypeId?: number;
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.PersonInterface */
export interface PersonInterface {
  id?: string;
  fName?: string;
  mName?: string;
  lName?: string;
  email?: string;
  phone?: string;
  dept?: string;
  pos?: string;
  source?: string;
  title?: string;
  formattedName?: string;
}

/** org.unitime.timetable.gwt.client.access.AccessControlInterface.PingRequest */
export interface PingRequest {
  page?: string;
  active?: boolean;
  operation?: AccessControlInterface_Operation;
}

/** org.unitime.timetable.gwt.client.access.AccessControlInterface.PingResponse */
export interface PingResponse {
  access?: boolean;
  inactive?: number;
  queue?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.PositionInterface */
export interface PositionInterface {
  id?: number;
  abbv?: string;
  label?: string;
  sortOrder?: number;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.PrefLevel */
export interface PrefLevel {
  id?: number;
  label?: string;
  title?: string;
  color?: string;
  code?: string;
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.Preference */
export interface Preference {
  ownerId?: number;
  preferenceId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.PreferenceInfo */
export interface PreferenceInfo {
  ownerId?: number;
  name?: string;
  preference?: string;
  comparable?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.PreferenceInterface */
export interface PreferenceInterface {
  code?: string;
  name?: string;
  abbv?: string;
  color?: string;
  id?: number;
  editable?: boolean;
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.Preferences */
export interface Preferences {
  id?: number;
  type?: string;
  items?: IdLabel[];
  selections?: Selection[];
}

/** org.unitime.timetable.gwt.shared.SolverInterface.ProgressMessage */
export interface ProgressMessage {
  level?: ProgressLogLevel;
  date?: string;
  message?: string;
  stackTrace?: string[];
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.Properties */
export interface Properties {
  offering?: Offering;
  preferences?: InstructorInterface_PreferenceInterface[];
  instructors?: InstructorInterface2[];
  attributes?: AttributeInterface[];
  responsibilities?: Responsibility[];
}

/** org.unitime.timetable.gwt.shared.PublishedSectioningSolutionInterface */
export interface PublishedSectioningSolutionInterface {
  uniqueId?: number;
  timeStamp?: string;
  owner?: string;
  info?: { [key: string]: string };
  loaded?: boolean;
  clonned?: boolean;
  selected?: boolean;
  canSelect?: boolean;
  canClone?: boolean;
  canLoad?: boolean;
  canChangeNote?: boolean;
  config?: string;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.PublishedSectioningSolutionInterface.PublishedSectioningSolutionsRequest */
export interface PublishedSectioningSolutionsRequest {
  operation?: PublishedSectioningSolutionInterface_Operation;
  uniqueId?: number;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.Query */
export interface Query {
  id?: number;
  name?: string;
  description?: string;
  query?: string;
  flags?: number;
  parameters?: SavedHQLInterface_Parameter[];
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.QueueItemInterface */
export interface QueueItemInterface {
  id?: string;
  name?: string;
  status?: string;
  progress?: string;
  owner?: string;
  session?: string;
  output?: string;
  log?: string;
  host?: string;
  outputLink?: string;
  created?: string;
  started?: string;
  finished?: string;
  canDelete?: boolean;
  executionRequest?: ExecuteScriptRpcRequest;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.Record */
export interface Record {
  uniqueId?: number;
  values?: string[];
  editable?: boolean[];
  visible?: boolean[];
  deletable?: boolean;
}

/** org.unitime.timetable.gwt.shared.ClassSetupInterface.Reference */
export interface Reference {
  id?: number;
  reference?: string;
  label?: string;
  selectable?: boolean;
}

/** org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectInterface */
export interface RelatedObjectInterface {
  uniqueId?: number;
  type?: RelatedObjectType;
  courseIds?: number[];
  courseNames?: string[];
  courseTitles?: string[];
  name?: string;
  note?: string;
  instruction?: string;
  instructionType?: number;
  maxCapacity?: number;
  instructors?: ContactInterface[];
  locations?: ResourceInterface[];
  date?: string;
  time?: string;
  conflicts?: string;
  dayOfYear?: number;
  startSlot?: number;
  endSlot?: number;
  externalIds?: string[];
  sectionNumber?: string;
  selection?: number[];
  detailPage?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectLookupRpcRequest */
export interface RelatedObjectLookupRpcRequest extends EventRpcRequest {
  uniqueId?: number;
  courseId?: number;
  level?: Level;
}

/** org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectLookupRpcResponse */
export interface RelatedObjectLookupRpcResponse {
  level?: Level;
  uniqueId?: number;
  name?: string;
  text?: string;
  relatedObject?: RelatedObjectInterface;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.RemoveAllClassInstructorsDataRpcRequest */
export interface RemoveAllClassInstructorsDataRpcRequest extends SimpleEditRpcRequest {
  data?: AssignClassInstructorsInterface;
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.Report */
export interface Report {
  id?: string;
  name?: string;
  description?: string;
  parameters?: Parameter[];
  flags?: number;
}

/** org.unitime.timetable.gwt.client.sectioning.SectioningReports.ReportTypeInterface */
export interface ReportTypeInterface {
  reference?: string;
  name?: string;
  implementation?: string;
  parameters?: string[];
  filter?: boolean;
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.Request */
export interface Request {
  teachingLoad?: number;
  sameCoursePref?: number;
  sameCommonPref?: number;
  teachingResponsibility?: Responsibility;
  instructorPreferences?: Preference[];
  attributePreferences?: Preference[];
  assignCoordinator?: boolean;
  percentShare?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.RequestSessionDetails */
export interface RequestSessionDetails extends EventRpcRequest {
}

/** org.unitime.timetable.gwt.shared.CourseRequestInterface.RequestedCourse */
export interface RequestedCourse {
  courseId?: number;
  courseName?: string;
  courseTitle?: string;
  readOnly?: boolean;
  canDelete?: boolean;
  canChangeAlternatives?: boolean;
  canChangePriority?: boolean;
  freeTime?: FreeTime[];
  selectedIntructionalMethods?: CourseRequestInterface_Preference[];
  selectedClasses?: CourseRequestInterface_Preference[];
  credit?: number[];
  status?: RequestedCourseStatus;
  statusNote?: string;
  overrideExternalId?: string;
  overrideTimeStamp?: string;
  requestorNote?: string;
  requestorNoteSuggestions?: string[];
  requestId?: string;
  inactive?: boolean;
  canWaitList?: boolean;
  waitListPosition?: string;
  changedBy?: string;
  timeStamp?: string;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.ReservationDefaultExpirationDatesRpcRequest */
export interface ReservationDefaultExpirationDatesRpcRequest {
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.ReservationFilterRpcRequest */
export interface ReservationFilterRpcRequest extends FilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.ReservationInterface */
export interface ReservationInterface {
  id?: number;
  offering?: Offering;
  configs?: Config[];
  classes?: Clazz[];
  limit?: number;
  enrollment?: number;
  lastLike?: number;
  projection?: number;
  startDate?: string;
  expirationDate?: string;
  editable?: boolean;
  expired?: boolean;
  override?: boolean;
  alwaysExpired?: boolean;
  allowOverlaps?: boolean;
  overLimit?: boolean;
  mustBeUsed?: boolean;
  inclusive?: boolean;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Course */
export interface ReservationInterface_Course extends IdName {
  control?: boolean;
}

/** org.unitime.timetable.gwt.shared.ReservationInterface.Subpart */
export interface ReservationInterface_Subpart extends IdName {
  classes?: Clazz[];
  config?: Config;
}

/** org.unitime.timetable.gwt.shared.EventInterface.ResourceInterface */
export interface ResourceInterface {
  resourceType?: ResourceType;
  resourceId?: number;
  externalId?: string;
  abbreviation?: string;
  resourceName?: string;
  title?: string;
  size?: number;
  distance?: number;
  roomType?: string;
  breakTime?: number;
  message?: string;
  ignoreRoomCheck?: boolean;
  displayName?: string;
  showMessageInGrid?: boolean;
  partitionParentId?: number;
  eventEmail?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.ResourceLookupRpcRequest */
export interface ResourceLookupRpcRequest extends EventRpcRequest {
  resourceType?: ResourceType;
  name?: string;
  limit?: number;
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.Responsibility */
export interface Responsibility extends IdName {
  coordinator?: boolean;
  instructor?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.ResponsibilityInterface */
export interface ResponsibilityInterface {
  id?: number;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.RetrieveAllSpecialRegistrationsRequest */
export interface RetrieveAllSpecialRegistrationsRequest extends StudentSectioningContext {
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.RetrieveAvailableGradeModesRequest */
export interface RetrieveAvailableGradeModesRequest extends StudentSectioningContext {
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.RetrieveAvailableGradeModesResponse */
export interface RetrieveAvailableGradeModesResponse {
  modes?: { [key: string]: SpecialRegistrationGradeModeChanges };
  varCreds?: { [key: string]: SpecialRegistrationVariableCreditChange };
  maxCredit?: number;
  currentCredit?: number;
  suggestions?: string[];
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.RetrieveCourseDetail */
export interface RetrieveCourseDetail {
  sessionId?: number;
  course?: string;
  courseId?: number;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.RetrieveSpecialRegistrationResponse */
export interface RetrieveSpecialRegistrationResponse {
  status?: SpecialRegistrationStatus;
  submitDate?: string;
  requestId?: string;
  description?: string;
  notes?: { [key: string]: string };
  changes?: ClassAssignment[];
  canCancel?: boolean;
  hasTimeConflict?: boolean;
  hasSpaceConflict?: boolean;
  extended?: boolean;
  hasLinkedConflict?: boolean;
  errors?: ErrorMessage[];
  maxCredit?: number;
  suggestions?: string[];
}

/** org.unitime.timetable.gwt.client.rooms.TravelTimes.Room */
export interface Room {
  id?: number;
  name?: string;
  building?: Building;
  travelTimes?: { [key: string]: number };
  distances?: { [key: string]: number };
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomDetailInterface */
export interface RoomDetailInterface extends Entity {
  externalId?: string;
  building?: BuildingInterface;
  roomType?: RoomTypeInterface;
  capacity?: number;
  examCapacity?: number;
  x?: number;
  y?: number;
  area?: number;
  controlDepartment?: RoomInterface_DepartmentInterface;
  eventDepartment?: RoomInterface_DepartmentInterface;
  departments?: RoomInterface_DepartmentInterface[];
  groups?: GroupInterface[];
  features?: FeatureInterface[];
  examTypes?: ExamTypeInterface[];
  ignoreTooFar?: boolean;
  ignoreRoomCheck?: boolean;
  periodPreference?: string;
  availability?: string;
  eventAvailability?: string;
  roomSharingNote?: string;
  eventNote?: string;
  defaultEventNote?: string;
  eventStatus?: number;
  breakTime?: number;
  defaultEventStatus?: number;
  defaultBreakTime?: number;
  prefix?: string;
  canShowDetail?: boolean;
  canSeeAvailability?: boolean;
  canSeePeriodPreferences?: boolean;
  canSeeEventAvailability?: boolean;
  canChange?: boolean;
  canChangeAvailability?: boolean;
  canChangeControll?: boolean;
  canChangeExternalId?: boolean;
  canChangeType?: boolean;
  canChangeCapacity?: boolean;
  canChangeExamStatus?: boolean;
  canChangeRoomProperties?: boolean;
  canChangeEventProperties?: boolean;
  canChangePicture?: boolean;
  canChangePreferences?: boolean;
  canChangeGroups?: boolean;
  canChangeFeatures?: boolean;
  canChangeEventAvailability?: boolean;
  canDelete?: boolean;
  miniMapUrl?: string;
  mapUrl?: string;
  pictures?: RoomPictureInterface[];
  lastChange?: string;
  roomSharingModel?: RoomSharingModel;
  eventAvailabilityModel?: RoomSharingModel;
  periodPreferenceModels?: { [key: string]: PeriodPreferenceModel };
  futureRooms?: FutureRoomInterface[];
  sessionId?: number;
  sessionName?: string;
  services?: EventServiceProviderInterface[];
  parent?: RoomDetailInterface;
  defaultEventEmail?: string;
  eventEmail?: string;
  url?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomDetailsRequest */
export interface RoomDetailsRequest {
  locationIds?: number[];
  department?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.RoomFilterRpcRequest */
export interface RoomFilterRpcRequest extends FilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomHintRequest */
export interface RoomHintRequest {
  locationId?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomHintResponse */
export interface RoomHintResponse {
  id?: number;
  label?: string;
  displayName?: string;
  roomTypeLabel?: string;
  miniMapUrl?: string;
  capacity?: number;
  examCapacity?: number;
  breakTime?: number;
  examType?: string;
  area?: string;
  groups?: GroupInterface[];
  eventStatus?: string;
  eventDepartment?: string;
  note?: string;
  ignoreRoomCheck?: boolean;
  url?: string;
  features?: FeatureInterface[];
  pictures?: RoomPictureInterface[];
  services?: EventServiceProviderInterface[];
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.RoomInfo */
export interface RoomInfo {
  name?: string;
  roomId?: number;
  pref?: number;
  size?: number;
  strike?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.AcademicSessionInterface */
export interface RoomInterface_AcademicSessionInterface {
  id?: number;
  label?: string;
  canAddRoom?: boolean;
  canAddNonUniversity?: boolean;
  canAddDepartmentalRoomGroup?: boolean;
  canAddGlobalRoomGroup?: boolean;
  canAddDepartmentalRoomFeature?: boolean;
  canAddGlobalRoomFeature?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.DepartmentInterface */
export interface RoomInterface_DepartmentInterface extends RoomPropertyInterface {
  code?: string;
  external?: boolean;
  event?: boolean;
  externalAbbv?: string;
  externalLabel?: string;
  preference?: PreferenceInterface;
  canEditRoomSharing?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomFilterRpcRequest */
export interface RoomInterface_RoomFilterRpcRequest extends RoomFilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPictureInterface */
export interface RoomPictureInterface {
  uniqueId?: number;
  name?: string;
  type?: string;
  timeStamp?: number;
  pictureType?: AttachmentTypeInterface;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPictureRequest */
export interface RoomPictureRequest {
  operation?: RoomPictureRequest_Operation;
  apply?: Apply;
  sessionId?: number;
  locationId?: number;
  pictures?: RoomPictureInterface[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPictureResponse */
export interface RoomPictureResponse {
  name?: string;
  pictures?: RoomPictureInterface[];
  apply?: Apply;
  pictureTypes?: AttachmentTypeInterface[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPropertiesInterface */
export interface RoomPropertiesInterface extends MapPropertiesInterface {
  session?: RoomInterface_AcademicSessionInterface;
  canExportPdf?: boolean;
  canExportCsv?: boolean;
  canEditDepartments?: boolean;
  canEditRoomExams?: boolean;
  roomTypes?: RoomTypeInterface[];
  buildings?: BuildingInterface[];
  featureTypes?: FeatureTypeInterface[];
  departments?: RoomInterface_DepartmentInterface[];
  nrDepartments?: number;
  examTypes?: ExamTypeInterface[];
  groups?: GroupInterface[];
  features?: FeatureInterface[];
  preferences?: PreferenceInterface[];
  pictureTypes?: AttachmentTypeInterface[];
  canSeeCourses?: boolean;
  canSeeExams?: boolean;
  canSeeEvents?: boolean;
  gridAsText?: boolean;
  horizontal?: boolean;
  modes?: RoomSharingDisplayMode[];
  ellipsoid?: string;
  canChangeAvailability?: boolean;
  canChangeControll?: boolean;
  canChangeExternalId?: boolean;
  canChangeExamStatus?: boolean;
  canChangeEventProperties?: boolean;
  canChangePicture?: boolean;
  canChangePreferences?: boolean;
  canChangeGroups?: boolean;
  canChangeFeatures?: boolean;
  canChangeEventAvailability?: boolean;
  futureSessions?: RoomInterface_AcademicSessionInterface[];
  canExportRoomGroups?: boolean;
  canChangeDefaultGroup?: boolean;
  canExportRoomFeatures?: boolean;
  roomAreaMetricUnits?: boolean;
  canSaveFilterDefaults?: boolean;
  filterDefaults?: { [key: string]: string };
  eventServiceProviders?: EventServiceProviderInterface[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPropertiesRequest */
export interface RoomPropertiesRequest {
  sessionId?: number;
  mode?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomPropertyInterface */
export interface RoomPropertyInterface {
  id?: number;
  abbv?: string;
  label?: string;
  color?: string;
  title?: string;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomSharingDisplayMode */
export interface RoomSharingDisplayMode {
  name?: string;
  firstDay?: number;
  lastDay?: number;
  firstSlot?: number;
  lastSlot?: number;
  step?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomSharingModel */
export interface RoomSharingModel {
  id?: number;
  name?: string;
  defaultOption?: number;
  defaultHorizontal?: boolean;
  modes?: RoomSharingDisplayMode[];
  options?: RoomSharingOption[];
  otherOptions?: RoomSharingOption[];
  model?: { [key: string]: { [key: string]: number } };
  editable?: { [key: string]: { [key: string]: boolean } };
  defaultMode?: number;
  defaultEditable?: boolean;
  note?: string;
  noteEditable?: boolean;
  preferences?: PreferenceInterface[];
  defaultPreference?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomSharingOption */
export interface RoomSharingOption {
  code?: string;
  name?: string;
  color?: string;
  id?: number;
  editable?: boolean;
  preferenceId?: number;
  deletable?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomSharingRequest */
export interface RoomSharingRequest {
  operation?: RoomSharingRequest_Operation;
  sessionId?: number;
  locationId?: number;
  model?: RoomSharingModel;
  eventAvailability?: boolean;
  includeRoomPreferences?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomTypeInterface */
export interface RoomTypeInterface {
  id?: number;
  reference?: string;
  label?: string;
  room?: boolean;
  order?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.RoomUpdateRpcRequest */
export interface RoomUpdateRpcRequest {
  sessionId?: number;
  operation?: RoomUpdateRpcRequest_Operation;
  locationId?: number;
  room?: RoomDetailInterface;
  futureFlags?: { [key: string]: number };
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.SaveDataGoToNextRpcRequest */
export interface SaveDataGoToNextRpcRequest extends SimpleEditRpcRequest {
  data?: AssignClassInstructorsInterface;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.SaveDataGoToPreviousRpcRequest */
export interface SaveDataGoToPreviousRpcRequest extends SimpleEditRpcRequest {
  data?: AssignClassInstructorsInterface;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.SaveDataRpcRequest */
export interface SaveDataRpcRequest extends SimpleEditRpcRequest {
  data?: AssignClassInstructorsInterface;
}

/** org.unitime.timetable.gwt.shared.EventInterface.SaveEventRpcRequest */
export interface SaveEventRpcRequest extends SaveOrApproveEventRpcRequest {
}

/** org.unitime.timetable.gwt.shared.EventInterface.SaveFilterDefaultRpcRequest */
export interface SaveFilterDefaultRpcRequest {
  name?: string;
  value?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.SaveOrApproveEventRpcRequest */
export interface SaveOrApproveEventRpcRequest extends EventRpcRequest {
  event?: EventInterface;
  message?: string;
  emailConfirmation?: boolean;
}

/** org.unitime.timetable.gwt.shared.EventInterface.SaveOrApproveEventRpcResponse */
export interface SaveOrApproveEventRpcResponse {
  event?: EventInterface;
  messages?: MessageInterface[];
  notes?: NoteInterface[];
  updatedMeetings?: MeetingInterface[];
  createdMeetings?: MeetingInterface[];
  deletedMeetings?: MeetingInterface[];
  cancelledMeetings?: MeetingInterface[];
  approvedMeetings?: MeetingInterface[];
  addedServices?: EventServiceProviderInterface[];
  removedServices?: EventServiceProviderInterface[];
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.SaveOrUpdateScriptRpcRequest */
export interface SaveOrUpdateScriptRpcRequest {
  script?: ScriptInterface;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.SaveRecordRpcRequest */
export interface SaveRecordRpcRequest extends SimpleEditInterface_SimpleEditRpcRequest {
  record?: SimpleEditInterface_Record;
}

/** org.unitime.timetable.gwt.shared.TeachingRequestInterface.SaveRequestsRpcRequest */
export interface SaveRequestsRpcRequest {
  offeringId?: number;
  requests?: Request[];
}

/** org.unitime.timetable.gwt.shared.TaskInterface.SaveTaskDetailsRpcRequest */
export interface SaveTaskDetailsRpcRequest {
  task?: TaskInterface;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.Flag */
export interface SavedHQLInterface_Flag {
  value?: number;
  text?: string;
  appearance?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.IdValue */
export interface SavedHQLInterface_IdValue {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.ListItem */
export interface SavedHQLInterface_ListItem {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.Parameter */
export interface SavedHQLInterface_Parameter {
  name?: string;
  label?: string;
  type?: string;
  value?: string;
  default?: string;
  options?: SavedHQLInterface_ListItem[];
  multiSelect?: boolean;
}

/** org.unitime.timetable.gwt.shared.SavedHQLInterface.Table */
export interface SavedHQLInterface_Table {
  data?: string[][];
}

/** org.unitime.timetable.gwt.shared.ScriptInterface */
export interface ScriptInterface {
  id?: number;
  name?: string;
  description?: string;
  engine?: string;
  permission?: string;
  script?: string;
  parameters?: ScriptParameterInterface[];
  canEdit?: boolean;
  canDelete?: boolean;
  canExecute?: boolean;
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.ListItem */
export interface ScriptInterface_ListItem {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.ScriptOptionsInterface */
export interface ScriptOptionsInterface {
  engines?: string[];
  permissions?: string[];
  canAdd?: boolean;
  email?: string;
}

/** org.unitime.timetable.gwt.shared.ScriptInterface.ScriptParameterInterface */
export interface ScriptParameterInterface {
  name?: string;
  label?: string;
  type?: string;
  value?: string;
  default?: string;
  options?: ScriptInterface_ListItem[];
  multiSelect?: boolean;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.SearchRoomFeaturesRequest */
export interface SearchRoomFeaturesRequest {
  filter?: RoomFilterRpcRequest;
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.RoomInterface.SearchRoomGroupsRequest */
export interface SearchRoomGroupsRequest {
  filter?: RoomFilterRpcRequest;
  sessionId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.SectionInfo */
export interface SectionInfo {
  sectionId?: number;
  sectionName?: string;
  externalId?: string;
  type?: string;
  common?: boolean;
  time?: string;
  date?: string;
  room?: string;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.SectioningAction */
export interface SectioningAction {
  logId?: number;
  student?: Student;
  timeStamp?: string;
  operation?: string;
  user?: string;
  message?: string;
  result?: string;
  cpuTime?: number;
  wallTime?: number;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.SectioningProperties */
export interface SectioningProperties {
  sessionId?: number;
  admin?: boolean;
  advisor?: boolean;
  email?: boolean;
  massCancel?: boolean;
  changeStatus?: boolean;
  requestUpdate?: boolean;
  reloadStudent?: boolean;
  changeLog?: boolean;
  checkStudentOverrides?: boolean;
  validateStudentOverrides?: boolean;
  recheckCriticalCourses?: boolean;
  advisorCourseRequests?: boolean;
  editableGroups?: StudentGroupInfo[];
  emailOptionalToggleCaption?: string;
  emailOptionalToggleDefault?: boolean;
  releasePins?: boolean;
  retrievePins?: boolean;
}

/** org.unitime.timetable.gwt.client.sectioning.SectioningReports.SectioningReportRpcRequest */
export interface SectioningReportRpcRequest {
  parameters?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.client.sectioning.SectioningReports.SectioningReportRpcResponse */
export interface SectioningReportRpcResponse {
  report?: string[][];
}

/** org.unitime.timetable.gwt.client.sectioning.SectioningReports.SectioningReportTypesRpcRequest */
export interface SectioningReportTypesRpcRequest {
  online?: boolean;
}

/** org.unitime.timetable.gwt.client.sectioning.SectioningStatusFilterBox.SectioningStatusFilterRpcRequest */
export interface SectioningStatusFilterRpcRequest extends FilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.SelectedAssignment */
export interface SelectedAssignment {
  classId?: number;
  days?: number;
  startSlot?: number;
  roomIds?: number[];
  patternId?: number;
  datePatternId?: number;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.SelectedAssignmentsRequest */
export interface SelectedAssignmentsRequest {
  classId?: number;
  assignments?: SelectedAssignment[];
}

/** org.unitime.timetable.gwt.client.instructor.survey.InstructorSurveyInterface.Selection */
export interface Selection {
  item?: number;
  level?: number;
  instructorLevel?: number;
  note?: string;
  problem?: Problem;
}

/** org.unitime.timetable.gwt.shared.EventInterface.SendStudentEmailsRpcRequest */
export interface SendStudentEmailsRpcRequest {
  eventId?: number;
  studentIds?: number[];
  cC?: string;
  subject?: string;
  message?: string;
}

/** org.unitime.timetable.gwt.client.widgets.ServerDateTimeFormat.ServerTimeZoneRequest */
export interface ServerTimeZoneRequest {
}

/** org.unitime.timetable.gwt.client.widgets.ServerDateTimeFormat.ServerTimeZoneResponse */
export interface ServerTimeZoneResponse {
  id?: string;
  names?: string[];
  timeZoneOffsetInMinutes?: number;
  transitions?: number[];
}

/** org.unitime.timetable.gwt.shared.MenuInterface.SessionInfoInterface */
export interface SessionInfoInterface extends InfoInterface {
  session?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.SessionInfoRpcRequest */
export interface SessionInfoRpcRequest {
}

/** org.unitime.timetable.gwt.shared.EventInterface.SessionInterface */
export interface SessionInterface {
  sessionId?: number;
  term?: string;
  year?: string;
  initiative?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.SessionMonth */
export interface SessionMonth {
  year?: number;
  month?: number;
  days?: number[];
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.SetLastDepartmentRequest */
export interface SetLastDepartmentRequest {
  departmentId?: number;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface */
export interface SimpleEditInterface {
  records?: SimpleEditInterface_Record[];
  fields?: SimpleEditInterface_Field[];
  editable?: boolean;
  addable?: boolean;
  saveOrder?: boolean;
  canMoveUpAndDown?: boolean;
  allowSort?: boolean;
  sort?: number[];
  sessionId?: number;
  sessionName?: string;
  pageName?: SimpleEditInterface_PageName;
  confirmDelete?: string;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.Field */
export interface SimpleEditInterface_Field {
  name?: string;
  type?: SimpleEditInterface_FieldType;
  length?: number;
  width?: number;
  height?: number;
  flags?: number;
  values?: SimpleEditInterface_ListItem[];
  default?: string;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.GetPageNameRpcRequest */
export interface SimpleEditInterface_GetPageNameRpcRequest extends SimpleEditInterface_SimpleEditRpcRequest {
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.ListItem */
export interface SimpleEditInterface_ListItem {
  value?: string;
  text?: string;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.LoadDataRpcRequest */
export interface SimpleEditInterface_LoadDataRpcRequest extends SimpleEditInterface_SimpleEditRpcRequest {
  filter?: string[];
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.PageName */
export interface SimpleEditInterface_PageName {
  singular?: string;
  plural?: string;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.Record */
export interface SimpleEditInterface_Record {
  uniqueId?: number;
  values?: string[];
  editable?: boolean[];
  deletable?: boolean;
  order?: number;
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.SaveDataRpcRequest */
export interface SimpleEditInterface_SaveDataRpcRequest extends SimpleEditInterface_SimpleEditRpcRequest {
  data?: SimpleEditInterface;
  filter?: string[];
}

/** org.unitime.timetable.gwt.shared.SimpleEditInterface.SimpleEditRpcRequest */
export interface SimpleEditInterface_SimpleEditRpcRequest {
  type?: string;
}

/** org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface.SimpleEditRpcRequest */
export interface SimpleEditRpcRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolutionChangesFilterRequest */
export interface SolutionChangesFilterRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolutionChangesFilterResponse */
export interface SolutionChangesFilterResponse extends AssignedClassesFilterResponse {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolutionChangesRequest */
export interface SolutionChangesRequest {
  filter?: FilterInterface;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolutionChangesResponse */
export interface SolutionChangesResponse extends AssignedClassesResponse {
  message?: string;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolutionInfo */
export interface SolutionInfo {
  id?: number;
  pairs?: InfoPair[];
  name?: string;
  log?: ProgressMessage[];
  created?: string;
  committed?: string;
  note?: string;
  owner?: string;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolutionLog */
export interface SolutionLog {
  log?: ProgressMessage[];
  owner?: string;
}

/** org.unitime.timetable.gwt.client.solver.SolverAllocatedMemory.SolverAllocatedMemoryRpcRequest */
export interface SolverAllocatedMemoryRpcRequest {
  solverId?: string;
}

/** org.unitime.timetable.gwt.client.solver.SolverAllocatedMemory.SolverAllocatedMemoryRpcResponse */
export interface SolverAllocatedMemoryRpcResponse {
  value?: string;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverConfiguration */
export interface SolverConfiguration {
  id?: number;
  name?: string;
  parameters?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.shared.MenuInterface.SolverInfoInterface */
export interface SolverInfoInterface extends InfoInterface {
  solver?: string;
  type?: string;
  url?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.SolverInfoRpcRequest */
export interface SolverInfoRpcRequest {
  includeSolutionInfo?: boolean;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverLogPageRequest */
export interface SolverLogPageRequest {
  level?: ProgressLogLevel;
  type?: SolverType;
  last?: string;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverLogPageResponse */
export interface SolverLogPageResponse {
  log?: ProgressMessage[];
  solutionLogs?: SolutionLog[];
  level?: ProgressLogLevel;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverOwner */
export interface SolverOwner {
  id?: number;
  name?: string;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverPageMessages */
export interface SolverPageMessages {
  pageMessages?: PageMessage[];
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverPageMessagesRequest */
export interface SolverPageMessagesRequest {
  type?: SolverType;
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverPageRequest */
export interface SolverPageRequest {
  type?: SolverType;
  operation?: SolverOperation;
  ownerIds?: number[];
  configurationId?: number;
  host?: string;
  parameters?: { [key: string]: string };
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverPageResponse */
export interface SolverPageResponse {
  loadDate?: string;
  solverType?: SolverType;
  operation?: SolverOperation;
  solverStatus?: string;
  solverProgress?: string;
  configurationId?: number;
  configurations?: SolverConfiguration[];
  parameters?: SolverParameter[];
  ownerIds?: number[];
  solverOwners?: SolverOwner[];
  host?: string;
  hosts?: string[];
  currentSolution?: SolutionInfo;
  bestSolution?: SolutionInfo;
  selectedSolutions?: SolutionInfo[];
  log?: ProgressMessage[];
  operations?: number;
  allowMultipleOwners?: boolean;
  working?: boolean;
  refresh?: boolean;
  pageMessages?: PageMessage[];
}

/** org.unitime.timetable.gwt.shared.SolverInterface.SolverParameter */
export interface SolverParameter {
  id?: number;
  key?: string;
  type?: string;
  name?: string;
  value?: string;
  defaut?: string;
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolverReportsRequest */
export interface SolverReportsRequest {
}

/** org.unitime.timetable.gwt.shared.CourseTimetablingSolverInterface.SolverReportsResponse */
export interface SolverReportsResponse {
  pageMessages?: PageMessage[];
  tables?: TableInterface[];
  preferences?: PreferenceInterface[];
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationCreditChange */
export interface SpecialRegistrationCreditChange {
  subject?: string;
  course?: string;
  crn?: string;
  credit?: number;
  originalCredit?: number;
  approvals?: string[];
  note?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationEligibilityRequest */
export interface SpecialRegistrationEligibilityRequest extends StudentSectioningContext {
  requestId?: string;
  classAssignments?: ClassAssignment[];
  errors?: ErrorMessage[];
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationEligibilityResponse */
export interface SpecialRegistrationEligibilityResponse {
  message?: string;
  canSubmit?: boolean;
  errors?: ErrorMessage[];
  deniedErrors?: ErrorMessage[];
  cancelErrors?: ErrorMessage[];
  cancelRequestIds?: string[];
  credit?: number;
  suggestions?: string[];
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationGradeMode */
export interface SpecialRegistrationGradeMode extends GradeMode {
  approvals?: string[];
  disclaimer?: string;
  originalGradeMode?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationGradeModeChange */
export interface SpecialRegistrationGradeModeChange {
  subject?: string;
  course?: string;
  credit?: string;
  crn?: string[];
  approvals?: string[];
  originalGradeMode?: string;
  selectedGradeMode?: string;
  selectedGradeModeDescription?: string;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationGradeModeChanges */
export interface SpecialRegistrationGradeModeChanges {
  currentGradeMode?: SpecialRegistrationGradeMode;
  availableChanges?: SpecialRegistrationGradeMode[];
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.InstructorInfo */
export interface SpecialRegistrationInterface_InstructorInfo {
  instructorId?: number;
  instructorName?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SpecialRegistrationVariableCreditChange */
export interface SpecialRegistrationVariableCreditChange {
  approvals?: string[];
  availableCredits?: number[];
}

/** org.unitime.timetable.gwt.shared.EventInterface.SponsoringOrganizationInterface */
export interface SponsoringOrganizationInterface {
  name?: string;
  email?: string;
  uniqueId?: number;
}

/** org.unitime.timetable.gwt.shared.EventInterface.StandardEventNoteInterface */
export interface StandardEventNoteInterface {
  id?: number;
  reference?: string;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.DepartmentInterface.StatusOption */
export interface StatusOption {
  id?: number;
  reference?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.ClassAssignmentInterface.Student */
export interface Student {
  id?: number;
  sessionId?: number;
  externalId?: string;
  name?: string;
  email?: string;
  area?: CodeLabel[];
  classification?: CodeLabel[];
  major?: CodeLabel[];
  accommodation?: CodeLabel[];
  minor?: CodeLabel[];
  concentration?: CodeLabel[];
  degree?: CodeLabel[];
  program?: CodeLabel[];
  campus?: CodeLabel[];
  defaultCampus?: string;
  advisor?: string[];
  groups?: Group[];
  canShowExternalId?: boolean;
  canSelect?: boolean;
  canUseAssitant?: boolean;
  canRegister?: boolean;
  mode?: WaitListMode;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.StudentConflictInfo */
export interface StudentConflictInfo {
  info?: JenrlInfo;
  other?: ClassAssignmentDetails;
  another?: ClassAssignmentDetails;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.StudentGroupInfo */
export interface StudentGroupInfo {
  uniqueId?: number;
  reference?: string;
  label?: string;
  type?: string;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.StudentInfo */
export interface StudentInfo {
  studentId?: number;
  sessionId?: number;
  studentName?: string;
  studentExternalId?: string;
  studentEmail?: string;
  sessionName?: string;
}

/** org.unitime.timetable.gwt.shared.StudentSchedulingPreferencesInterface */
export interface StudentSchedulingPreferencesInterface {
  classModality?: ClassModality;
  scheduleGaps?: ScheduleGaps;
  allowClassDates?: boolean;
  allowRequireOnline?: boolean;
  classDateFrom?: string;
  classDateTo?: string;
  customNote?: string;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.StudentSectioningContext */
export interface StudentSectioningContext {
  online?: boolean;
  sectioning?: boolean;
  sessionId?: number;
  studentId?: number;
  pin?: string;
  sessionDates?: SessionMonth[];
  classScheduleNotAvailable?: boolean;
}

/** org.unitime.timetable.gwt.shared.OnlineSectioningInterface.StudentStatusInfo */
export interface StudentStatusInfo {
  uniqueId?: number;
  reference?: string;
  label?: string;
  assistantPage?: boolean;
  requestsPage?: boolean;
  regStudent?: boolean;
  regAdvisor?: boolean;
  regAdmin?: boolean;
  enrlStudent?: boolean;
  enrlAdvisor?: boolean;
  enrlAdmin?: boolean;
  waitList?: boolean;
  noSubs?: boolean;
  email?: boolean;
  canRequire?: boolean;
  specReg?: boolean;
  reqValidation?: boolean;
  noSchedule?: boolean;
  reSchedule?: boolean;
  courseTypes?: string;
  effectiveStart?: string;
  effectiveStop?: string;
  message?: string;
  fallback?: string;
  canUseAssitant?: boolean;
  canRegister?: boolean;
  notifications?: string;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.SubjectAreaInterface */
export interface SubjectAreaInterface {
  id?: number;
  abbv?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SubmitSpecialRegistrationRequest */
export interface SubmitSpecialRegistrationRequest extends StudentSectioningContext {
  requestId?: string;
  courses?: CourseRequestInterface;
  classAssignments?: ClassAssignment[];
  errors?: ErrorMessage[];
  note?: { [key: string]: string };
  credit?: number;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.SubmitSpecialRegistrationResponse */
export interface SubmitSpecialRegistrationResponse {
  requestId?: string;
  message?: string;
  success?: boolean;
  status?: SpecialRegistrationStatus;
  requests?: RetrieveSpecialRegistrationResponse[];
  cancelledRequestIds?: string[];
}

/** org.unitime.timetable.gwt.shared.ClassSetupInterface.Subpart */
export interface Subpart extends Reference {
  defaultDatePatternName?: string;
}

/** org.unitime.timetable.gwt.shared.InstrOfferingConfigInterface.SubpartLine */
export interface SubpartLine {
  subpartId?: number;
  iType?: number;
  editable?: boolean;
  canDelete?: boolean;
  locked?: boolean;
  minClassLimit?: number;
  maxClassLimit?: number;
  numberOfClasses?: number;
  numberOfRooms?: number;
  minutesPerWeek?: number;
  roomRatio?: number;
  parentId?: number;
  departmentId?: number;
  splitAttendance?: boolean;
  indent?: number;
  label?: string;
  error?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.Suggestion */
export interface Suggestion {
  value?: number;
  unassignedVariables?: number;
  unresolvedConflicts?: ClassAssignmentDetails[];
  differentAssignments?: ClassAssignmentDetails[];
  distributionConflicts?: DistributionInfo[];
  btbInstructorConflicts?: BtbInstructorInfo[];
  studentConflicts?: StudentConflictInfo[];
  canAssign?: boolean;
  placement?: ClassAssignmentDetails;
  selectedPlacement?: ClassAssignmentDetails;
  baseValue?: number;
  baseUnassignedVariables?: number;
  studentConflictSummary?: TableCellMulti;
  criteria?: { [key: string]: number };
  baseCriteria?: { [key: string]: number };
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.SuggestionInfo */
export interface SuggestionInfo {
  id?: number;
  assignments?: AssignmentInfo[];
  value?: number;
  values?: { [key: string]: number };
  nrConflicts?: number;
}

/** org.unitime.timetable.gwt.client.widgets.CourseNumbersSuggestBox.SuggestionInterface */
export interface SuggestionInterface {
  displayString?: string;
  replacementString?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.SuggestionProperties */
export interface SuggestionProperties {
  preferences?: SuggestionsInterface_PreferenceInterface[];
  solver?: boolean;
  selectedAssignments?: SelectedAssignment[];
  firstDay?: number;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.SuggestionPropertiesRequest */
export interface SuggestionPropertiesRequest {
  historyId?: number;
}

/** org.unitime.timetable.gwt.client.widgets.CourseNumbersSuggestBox.SuggestionRpcRequest */
export interface SuggestionRpcRequest {
  configuration?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.Suggestions */
export interface Suggestions {
  suggestions?: Suggestion[];
  timeoutReached?: boolean;
  nrCombinationsConsidered?: number;
  nrSolutions?: number;
  depth?: number;
  limit?: number;
  timeLimit?: number;
  classId?: number;
  allowBreakHard?: boolean;
  sameRoom?: boolean;
  sameTime?: boolean;
  placements?: boolean;
  baseSuggestion?: Suggestion;
  filter?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.SuggestionsFilterRpcRequest */
export interface SuggestionsFilterRpcRequest extends FilterRpcRequest {
  classId?: number;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.ClassInfo */
export interface SuggestionsInterface_ClassInfo {
  name?: string;
  classId?: number;
  pref?: string;
  roomCap?: number;
  nrRooms?: number;
  ord?: number;
  note?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.ComputeSuggestionsRequest */
export interface SuggestionsInterface_ComputeSuggestionsRequest {
  classId?: number;
  assignments?: SelectedAssignment[];
  depth?: number;
  limit?: number;
  timeLimit?: number;
  allowBreakHard?: boolean;
  sameRoom?: boolean;
  sameTime?: boolean;
  placements?: boolean;
  filter?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.InstructorInfo */
export interface SuggestionsInterface_InstructorInfo {
  name?: string;
  instructorId?: number;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.PreferenceInterface */
export interface SuggestionsInterface_PreferenceInterface extends PreferenceInterface {
  preference?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.SuggestionsResponse */
export interface SuggestionsResponse {
  assignment?: SuggestionInfo;
  suggestions?: SuggestionInfo[];
  timeoutReached?: boolean;
  nrCombinationsConsidered?: number;
  nrSolutions?: number;
  domainSize?: number;
  domain?: SuggestionInfo[];
}

/** org.unitime.timetable.gwt.shared.PointInTimeDataReportsInterface.Table */
export interface Table {
  data?: string[][];
}

/** org.unitime.timetable.gwt.shared.TableInterface.TableCellInterface */
export interface TableCellInterface {
  formattedValue?: string;
  styleName?: string;
  color?: string;
  value?: any;
  title?: string;
  underline?: boolean;
}

/** org.unitime.timetable.gwt.shared.TableInterface.TableCellMulti */
export interface TableCellMulti extends TableCellText {
  chunks?: TableCellInterface[];
}

/** org.unitime.timetable.gwt.shared.TableInterface.TableCellText */
export interface TableCellText extends TableCellInterface {
}

/** org.unitime.timetable.gwt.shared.TableInterface.TableHeaderIterface */
export interface TableHeaderIterface {
  name?: string;
  comparable?: boolean;
  alignment?: Alignment;
  visible?: boolean;
  description?: string;
}

/** org.unitime.timetable.gwt.shared.TableInterface */
export interface TableInterface {
  header?: TableHeaderIterface[];
  rows?: TableRowInterface[];
  name?: string;
  errorMessage?: string;
  showPrefLegend?: boolean;
  tableId?: string;
}

/** org.unitime.timetable.gwt.shared.TableInterface.TableRowInterface */
export interface TableRowInterface {
  cells?: TableCellInterface[];
  id?: number;
  link?: string;
  linkName?: string;
  selected?: boolean;
}

/** org.unitime.timetable.gwt.shared.TaskInterface.TaskExecutionInterface */
export interface TaskExecutionInterface {
  id?: number;
  dayOfYear?: number;
  slot?: number;
  status?: ExecutionStatus;
  created?: string;
  queued?: string;
  started?: string;
  finished?: string;
  output?: string;
  executionDate?: string;
  dayOfWeek?: number;
  statusMessage?: string;
}

/** org.unitime.timetable.gwt.shared.TaskInterface.TaskExecutionLogInterface */
export interface TaskExecutionLogInterface {
  log?: string;
}

/** org.unitime.timetable.gwt.shared.TaskInterface */
export interface TaskInterface {
  id?: number;
  name?: string;
  email?: string;
  script?: ScriptInterface;
  canEdit?: boolean;
  canView?: boolean;
  lastExecuted?: string;
  lastStatus?: ExecutionStatus;
  parameters?: { [key: string]: string };
  owner?: ContactInterface;
  executions?: TaskExecutionInterface[];
}

/** org.unitime.timetable.gwt.shared.TaskInterface.TaskOptionsInterface */
export interface TaskOptionsInterface {
  canAdd?: boolean;
  manager?: ContactInterface;
  scripts?: ScriptInterface[];
  session?: AcademicSessionInfo;
  months?: SessionMonth[];
  firstDayOfWeek?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingAssignmentsDetailRequest */
export interface TeachingAssignmentsDetailRequest {
  instructorId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingAssignmentsPageRequest */
export interface TeachingAssignmentsPageRequest {
  request?: TeachingRequestsFilterRpcRequest;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingRequestDetailRequest */
export interface TeachingRequestDetailRequest {
  requestId?: number;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingRequestInfo */
export interface TeachingRequestInfo {
  course?: CourseInfo;
  requestId?: number;
  load?: number;
  sections?: SectionInfo[];
  instructors?: InstructorInfo[];
  instructorPreferences?: PreferenceInfo[];
  attributePreferences?: PreferenceInfo[];
  values?: { [key: string]: number };
  nrInstructors?: number;
  conflict?: boolean;
  matching?: boolean;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingRequestsFilterRpcRequest */
export interface TeachingRequestsFilterRpcRequest extends FilterRpcRequest {
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingRequestsPagePropertiesRequest */
export interface TeachingRequestsPagePropertiesRequest {
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingRequestsPagePropertiesResponse */
export interface TeachingRequestsPagePropertiesResponse {
  subjecAreas?: InstructorInterface_SubjectAreaInterface[];
  departments?: InstructorInterface_DepartmentInterface[];
  preferences?: InstructorInterface_PreferenceInterface[];
  lastSubjectAreaId?: number;
  lastDepartmentId?: number;
  attributeTypes?: AttributeTypeInterface[];
  modes?: RoomSharingDisplayMode[];
  hasSolver?: boolean;
  availabilityModel?: InstructorAvailabilityModel;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.TeachingRequestsPageRequest */
export interface TeachingRequestsPageRequest {
  request?: TeachingRequestsFilterRpcRequest;
}

/** org.unitime.timetable.gwt.client.TimeHint.TimeHintRequest */
export interface TimeHintRequest {
  parameter?: string;
}

/** org.unitime.timetable.gwt.client.TimeHint.TimeHintResponse */
export interface TimeHintResponse {
  content?: string;
}

/** org.unitime.timetable.gwt.shared.SuggestionsInterface.TimeInfo */
export interface TimeInfo {
  days?: number;
  startSlot?: number;
  min?: number;
  pref?: number;
  strike?: boolean;
  patternId?: number;
  datePattern?: DateInfo;
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridBackground */
export interface TimetableGridBackground {
  day?: number;
  slot?: number;
  length?: number;
  background?: string;
  available?: boolean;
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridCell */
export interface TimetableGridCell {
  type?: Type;
  id?: number;
  names?: string[];
  titles?: string[];
  date?: string;
  time?: string;
  instructors?: string[];
  rooms?: string[];
  preference?: string;
  day?: number;
  slot?: number;
  length?: number;
  weekCode?: string;
  nrLines?: number;
  index?: number;
  background?: string;
  italics?: boolean;
  group?: string;
  committed?: boolean;
  properties?: { [key: string]: string };
  days?: string;
  nrLinesPerDate?: { [key: string]: number };
  indexPerDate?: { [key: string]: number };
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridFilterRequest */
export interface TimetableGridFilterRequest {
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridFilterResponse */
export interface TimetableGridFilterResponse extends FilterInterface {
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface */
export interface TimetableGridInterface {
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridLegend */
export interface TimetableGridLegend {
  color?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridModel */
export interface TimetableGridModel {
  resourceType?: number;
  resourceId?: number;
  externalId?: string;
  cells?: TimetableGridCell[];
  backgrounds?: TimetableGridBackground[];
  name?: string;
  size?: number;
  type?: number;
  firstDay?: number;
  firstSessionDay?: number;
  utilization?: number;
  firstDate?: string;
  nameColor?: string;
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridRequest */
export interface TimetableGridRequest {
  filter?: FilterInterface;
}

/** org.unitime.timetable.gwt.shared.TimetableGridInterface.TimetableGridResponse */
export interface TimetableGridResponse {
  models?: TimetableGridModel[];
  defaultDatePatternName?: string;
  pageMessages?: PageMessage[];
  assignedLegend?: TimetableGridLegend[];
  notAssignedLegend?: TimetableGridLegend[];
  weekOffset?: number;
}

/** org.unitime.timetable.gwt.client.rooms.TravelTimes.TravelTimeResponse */
export interface TravelTimeResponse {
  sessionId?: number;
  sessionName?: string;
  rooms?: Room[];
}

/** org.unitime.timetable.gwt.client.rooms.TravelTimes.TravelTimesRequest */
export interface TravelTimesRequest {
  command?: TravelTimesRequest_Command;
  rooms?: Room[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.UpdateBuildingRequest */
export interface UpdateBuildingRequest {
  action?: UpdateBuildingAction;
  building?: BuildingInterface;
  updateRoomCoordinates?: boolean;
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.UpdateCourseOfferingRequest */
export interface UpdateCourseOfferingRequest {
  action?: UpdateCourseOfferingAction;
  courseOffering?: CourseOfferingInterface;
}

/** org.unitime.timetable.gwt.client.departments.DepartmentsEdit.UpdateDepartmentRequest */
export interface UpdateDepartmentRequest {
  action?: UpdateDepartmentAction;
  department?: DepartmentInterface2;
}

/** org.unitime.timetable.gwt.shared.InstructorInterface.UpdateInstructorAttributeRequest */
export interface UpdateInstructorAttributeRequest {
  deleteAttributeId?: number;
  attribute?: AttributeInterface;
  addInstructors?: number[];
  dropInstructors?: number[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.UpdateRoomDepartmentsRequest */
export interface UpdateRoomDepartmentsRequest {
  sessionId?: number;
  department?: RoomInterface_DepartmentInterface;
  examType?: ExamTypeInterface;
  addLocations?: number[];
  dropLocations?: number[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.UpdateRoomFeatureRequest */
export interface UpdateRoomFeatureRequest {
  sessionId?: number;
  deleteFeatureId?: number;
  feature?: FeatureInterface;
  addLocations?: number[];
  dropLocations?: number[];
  futureSessions?: number[];
}

/** org.unitime.timetable.gwt.shared.RoomInterface.UpdateRoomGroupRequest */
export interface UpdateRoomGroupRequest {
  sessionId?: number;
  deleteGroupId?: number;
  group?: GroupInterface;
  addLocations?: number[];
  dropLocations?: number[];
  futureSessions?: number[];
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.UpdateSpecialRegistrationRequest */
export interface UpdateSpecialRegistrationRequest extends StudentSectioningContext {
  requestId?: string;
  courseId?: number;
  note?: string;
  preReg?: boolean;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.UpdateSpecialRegistrationResponse */
export interface UpdateSpecialRegistrationResponse {
  success?: boolean;
  message?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.UserInfoInterface */
export interface UserInfoInterface extends InfoInterface {
  chameleon?: boolean;
  name?: string;
  role?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.UserInfoRpcRequest */
export interface UserInfoRpcRequest {
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.VariableTitleCourseInfo */
export interface VariableTitleCourseInfo {
  title?: string;
  subject?: string;
  courseNbr?: string;
  defaultGradeModeCode?: string;
  gradeModes?: GradeMode[];
  availableCredits?: number[];
  startDate?: string;
  endDate?: string;
  instructors?: SpecialRegistrationInterface_InstructorInfo[];
  details?: string;
  disclaimer?: string;
  suggestions?: string[];
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.VariableTitleCourseRequest */
export interface VariableTitleCourseRequest extends StudentSectioningContext {
  course?: VariableTitleCourseInfo;
  title?: string;
  instructor?: SpecialRegistrationInterface_InstructorInfo;
  credit?: number;
  note?: string;
  startDate?: string;
  endDate?: string;
  checkIfExists?: boolean;
  gradeMode?: string;
  maxCredit?: number;
  section?: string;
}

/** org.unitime.timetable.gwt.shared.SpecialRegistrationInterface.VariableTitleCourseResponse */
export interface VariableTitleCourseResponse {
  course?: RequestedCourse;
  requests?: RetrieveSpecialRegistrationResponse[];
  cancelRequestIds?: string[];
}

/** org.unitime.timetable.gwt.shared.MenuInterface.VersionInfoInterface */
export interface VersionInfoInterface {
  version?: string;
  buildNumber?: string;
  releaseDate?: string;
}

/** org.unitime.timetable.gwt.shared.MenuInterface.VersionInfoRpcRequest */
export interface VersionInfoRpcRequest {
}

/** org.unitime.timetable.gwt.shared.CourseOfferingInterface.WaitListInterface */
export interface WaitListInterface {
  id?: number;
  value?: string;
  label?: string;
}

/** org.unitime.timetable.gwt.shared.EventInterface.WeekInterface */
export interface WeekInterface {
  dayOfYear?: number;
  dayNames?: DateInterface[];
}

/** org.unitime.timetable.gwt.client.widgets.WeekSelector.WeekSelectorRequest */
export interface WeekSelectorRequest {
  sessionId?: number;
}

