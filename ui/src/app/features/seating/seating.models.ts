/**
 * Models for ExamSeatingRequest, InvigilationRequest and MyExamsRequest
 * (org.unitime.timetable.gwt.shared.ExamSeatingInterface, public-field DTOs).
 */
export interface IdName {
  id?: number;
  name?: string;
  detail?: string;
}

export interface AllocationStatus {
  status?: 'DRAFT' | 'APPROVED' | 'PUBLISHED';
  approvedBy?: string;
  publishedBy?: string;
  note?: string;
  approvedTs?: string;
  publishedTs?: string;
  version?: number;
  changesSincePublication?: number;
  canApprove?: boolean;
  canEdit?: boolean;
}

export interface Issue {
  severity?: 'ERROR' | 'WARNING';
  type?: string;
  message?: string;
  periodId?: number;
  roomId?: number;
  studentId?: number;
  examId?: number;
  invigilatorId?: number;
  period?: string;
  room?: string;
  student?: string;
  exam?: string;
  invigilator?: string;
  seat?: string;
  action?: string;
}

export interface LogEntry {
  timeStamp?: string;
  action?: string;
  detail?: string;
  userName?: string;
  period?: string;
}

export interface Period {
  id?: number;
  name?: string;
  date?: string;
  nrExams?: number;
  nrStudents?: number;
  nrRooms?: number;
  nrSeated?: number;
}

export interface SeatLayout {
  roomId?: number;
  room?: string;
  capacity?: number;
  examCapacity?: number;
  rows?: number;
  columns?: number;
  frontRows?: number;
  blocked?: string | null;
  accessible?: string | null;
  generated?: boolean;
  usableSeats?: number;
}

export interface Arrangement {
  id?: number;
  studentId?: number;
  roomId?: number | null;
  studentName?: string;
  studentExternalId?: string;
  room?: string;
  note?: string;
  approvedBy?: string;
  separateRoom?: boolean;
  accessibleSeat?: boolean;
  frontSeat?: boolean;
  extraTimePct?: number | null;
}

export interface Seat {
  label?: string;
  row?: number;
  column?: number;
  blocked?: boolean;
  accessible?: boolean;
  front?: boolean;
  usable?: boolean;
  studentId?: number;
  examId?: number;
  studentName?: string;
  studentExternalId?: string;
  exam?: string;
  locked?: boolean;
  manual?: boolean;
  special?: boolean;
}

export interface RoomPlan {
  roomId?: number;
  room?: string;
  rows?: number;
  columns?: number;
  usableSeats?: number;
  nrSeated?: number;
  seats?: Seat[];
  exams?: IdName[];
}

export interface SeatRow {
  studentId?: number;
  examId?: number;
  roomId?: number;
  periodId?: number;
  studentName?: string;
  studentExternalId?: string;
  exam?: string;
  room?: string;
  seat?: string;
  period?: string;
  date?: string;
  time?: string;
  school?: string;
  locked?: boolean;
  manual?: boolean;
  special?: boolean;
  extraTimePct?: number;
}

export interface ExamSeatingRequest {
  operation: string;
  examTypeId?: number | null;
  periodId?: number | null;
  roomId?: number | null;
  studentId?: number;
  examId?: number;
  toRoomId?: number | null;
  seat?: string;
  toSeat?: string;
  sort?: string[];
  pattern?: string;
  keepLocked?: boolean;
  layout?: SeatLayout;
  arrangement?: Arrangement;
  id?: number;
  override?: boolean;
  note?: string;
}

export interface ExamSeatingResponse {
  examTypes?: IdName[];
  examTypeId?: number;
  periods?: Period[];
  rooms?: IdName[];
  plan?: RoomPlan;
  unseated?: SeatRow[];
  layouts?: SeatLayout[];
  arrangements?: Arrangement[];
  allRooms?: IdName[];
  issues?: Issue[];
  rows?: SeatRow[];
  log?: LogEntry[];
  status?: AllocationStatus;
  message?: string;
  canEdit?: boolean;
  nrStudents?: number;
  nrSeated?: number;
  nrErrors?: number;
  nrWarnings?: number;
}

export interface InvigilatorInfo {
  id?: number;
  departmentId?: number | null;
  instructorId?: number;
  externalId?: string;
  name?: string;
  email?: string;
  department?: string;
  dutyTypes?: string;
  note?: string;
  maxPerDay?: number | null;
  maxTotal?: number | null;
  active?: boolean;
  nrDuties?: number;
  availability?: IdName[];
}

export interface InvigilationConfig {
  studentsPerInvigilator: number;
  minPerRoom: number;
  extraSpecialRoom: number;
  reservesPerPeriod: number;
  defaultMaxPerDay: number;
  defaultMaxTotal: number;
  chiefPerRoom: boolean;
  avoidOwnExam: boolean;
}

export interface Duty {
  id?: number;
  invigilatorId?: number;
  periodId?: number;
  roomId?: number;
  invigilator?: string;
  dutyType?: string;
  period?: string;
  date?: string;
  time?: string;
  room?: string;
  locked?: boolean;
  manual?: boolean;
  problems?: string[];
}

export interface BoardCell {
  periodId?: number;
  roomId?: number;
  room?: string;
  students?: number;
  required?: number;
  assigned?: number;
  chiefRequired?: boolean;
  special?: boolean;
  duties?: Duty[];
}

export interface Candidate {
  invigilatorId?: number;
  name?: string;
  department?: string;
  reason?: string;
  load?: number;
  dutiesThatDay?: number;
  preferred?: boolean;
  suitable?: boolean;
}

export interface InvigilationRequest {
  operation: string;
  examTypeId?: number | null;
  periodId?: number | null;
  roomId?: number | null;
  invigilatorId?: number;
  dutyId?: number;
  dutyType?: string;
  keepLocked?: boolean;
  invigilator?: InvigilatorInfo;
  config?: InvigilationConfig;
  csv?: string;
  availability?: string;
  override?: boolean;
  note?: string;
}

export interface InvigilationResponse {
  examTypes?: IdName[];
  examTypeId?: number;
  periods?: Period[];
  board?: BoardCell[];
  invigilators?: InvigilatorInfo[];
  candidates?: Candidate[];
  config?: InvigilationConfig;
  issues?: Issue[];
  duties?: Duty[];
  log?: LogEntry[];
  departments?: IdName[];
  status?: AllocationStatus;
  message?: string;
  canEdit?: boolean;
  required?: number;
  assigned?: number;
  nrErrors?: number;
  nrWarnings?: number;
}

export interface MyExamsResponse {
  student?: boolean;
  invigilator?: boolean;
  name?: string;
  seats?: SeatRow[];
  seatsPublished?: boolean;
  duties?: Duty[];
  dutiesPublished?: boolean;
  periods?: Period[];
  availability?: IdName[];
  message?: string;
}
