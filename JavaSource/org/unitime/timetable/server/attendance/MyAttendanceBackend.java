/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
*/
package org.unitime.timetable.server.attendance;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.AttendanceInterface.ClassStat;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Config;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Excuse;
import org.unitime.timetable.gwt.shared.AttendanceInterface.MyAttendanceRequest;
import org.unitime.timetable.gwt.shared.AttendanceInterface.MyAttendanceResponse;
import org.unitime.timetable.gwt.shared.AttendanceInterface.RecordInfo;
import org.unitime.timetable.model.AttendanceExcuse;
import org.unitime.timetable.model.AttendanceRecord;
import org.unitime.timetable.model.AttendanceSession;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.dao.AttendanceSessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * My Attendance (MyAttendanceRequest): a student's attendance per class with the standing against the
 * configured thresholds, their records, QR check-in (token from the code shown in class, device check,
 * late after the configured minutes) and medical certificate / leave submission with an attachment.
 */
@GwtRpcImplements(MyAttendanceRequest.class)
public class MyAttendanceBackend implements GwtRpcImplementation<MyAttendanceRequest, MyAttendanceResponse> {
	private static final long MAX_FILE = 5 * 1024 * 1024;

	@Override
	public MyAttendanceResponse execute(MyAttendanceRequest request, SessionContext context) {
		context.checkPermission(Right.MyAttendance);
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		org.hibernate.Session hib = AttendanceSessionDAO.getInstance().getSession();
		Config cfg = AttendanceService.config(hib, sessionId);
		MyAttendanceResponse response = new MyAttendanceResponse();
		response.config = cfg;
		response.name = context.getUser().getName();
		Student student = (context.getUser().getExternalUserId() == null ? null : Student.findByExternalId(sessionId, context.getUser().getExternalUserId()));
		if (student == null) { response.message = "No student record matches your account in this academic session."; return response; }
		response.student = true;
		String op = (request.operation == null ? "LOAD" : request.operation);

		if ("CHECKIN".equals(op)) {
			if (!cfg.qrEnabled) throw new GwtRpcException("QR check-in is not enabled.");
			Meeting m = (request.meetingId == null ? null : AttendanceService.meeting(hib, request.meetingId));
			Class_ c = AttendanceService.classOf(m);
			if (c == null) throw new GwtRpcException("This check-in code is not valid.");
			AttendanceSession s = AttendanceService.session(hib, m.getUniqueId());
			Date now = new Date();
			if (s == null || "FINALISED".equals(s.getStatus()) || s.getCheckinUntil() == null || !s.getCheckinUntil().after(now))
				throw new GwtRpcException("Check-in for " + c.getClassLabel() + " is closed.");
			if (!AttendanceService.validToken(cfg, s, request.token))
				throw new GwtRpcException("This code is not valid or has expired. Scan the code currently shown in class.");
			boolean enrolled = false;
			for (Student st: AttendanceService.roster(hib, c.getUniqueId())) if (st.getUniqueId().equals(student.getUniqueId())) enrolled = true;
			if (!enrolled) throw new GwtRpcException("You are not enrolled in " + c.getClassLabel() + ".");
			List<AttendanceRecord> recs = AttendanceService.records(hib, s.getUniqueId());
			if (cfg.oneDevice && request.deviceId != null)
				for (AttendanceRecord r: recs)
					if (request.deviceId.equals(r.getDeviceId()) && !r.getStudentId().equals(student.getUniqueId()))
						throw new GwtRpcException("This device has already been used to check in another student for this class. Ask your lecturer to mark you.");
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceRecord mine = null;
				for (AttendanceRecord r: recs) if (r.getStudentId().equals(student.getUniqueId())) mine = r;
				String status = (s.getCheckinOpened() != null && now.after(AttendanceService.plusMinutes(s.getCheckinOpened(), cfg.lateMinutes)) ? AttendanceService.LATE : AttendanceService.PRESENT);
				if (mine == null) { mine = new AttendanceRecord(); mine.setAttendanceSessionId(s.getUniqueId()); mine.setStudentId(student.getUniqueId()); }
				else if (AttendanceService.PRESENT.equals(mine.getStatus()) || AttendanceService.LATE.equals(mine.getStatus())) status = mine.getStatus(); // repeat scan keeps the first status
				mine.setStatus(status); mine.setSource("QR"); mine.setMarkedTs(now); mine.setMarkedBy(AttendanceService.name(student)); mine.setDeviceId(request.deviceId);
				if (mine.getUniqueId() == null) hib.persist(mine); else hib.merge(mine);
				hib.flush(); tx.commit();
				response.checkinStatus = status;
				response.checkinClass = c.getClassLabel() + ", " + AttendanceService.DATE.format(m.getMeetingDate()) + " " + AttendanceService.time(m.getStartPeriod(), m.getStopPeriod());
			} catch (RuntimeException e) { tx.rollback(); throw e; }
		}

		if ("SUBMIT_EXCUSE".equals(op)) {
			Excuse x = request.excuse;
			if (x == null || x.type == null || x.from == null || x.to == null) throw new GwtRpcException("Choose the type and the dates.");
			Date fromDate = AttendanceBackend.day(x.from), toDate = AttendanceBackend.day(x.to);
			if (toDate.before(fromDate)) throw new GwtRpcException("The end date is before the start date.");
			byte[] file = null;
			if (x.fileBase64 != null && !x.fileBase64.isEmpty()) {
				file = Base64.getDecoder().decode(x.fileBase64);
				if (file.length > MAX_FILE) throw new GwtRpcException("The attachment is larger than 5 MB.");
			}
			if ("MC".equals(x.type) && file == null) throw new GwtRpcException("Attach the medical certificate.");
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceExcuse e = new AttendanceExcuse();
				e.setSessionId(sessionId); e.setStudentId(student.getUniqueId()); e.setExcuseType(x.type);
				e.setFromDate(fromDate); e.setToDate(toDate); e.setNote(AttendanceBackend.trim(x.note, 1000));
				e.setFileName(AttendanceBackend.trim(x.fileName, 260)); e.setFileType(AttendanceBackend.trim(x.fileType, 100)); e.setFileContent(file);
				e.setStatus("PENDING"); e.setSubmittedTs(new Date());
				hib.persist(e);
				AttendanceBackend.log(hib, context, sessionId, "SUBMIT_EXCUSE", AttendanceService.name(student) + " " + x.type + " " + x.from + " - " + x.to);
				hib.flush(); tx.commit();
				response.message = "Submitted. It will be reviewed by " + ("LECTURER".equalsIgnoreCase(cfg.excuseApprover) ? "your lecturer" : "the school office") + ".";
			} catch (RuntimeException e) { tx.rollback(); throw e; }
		}

		// ---- the student's attendance
		Map<Long, int[]> perClass = new HashMap<Long, int[]>();
		List<Object[]> rows = hib.createQuery("select s, r from AttendanceSession s, AttendanceRecord r where r.attendanceSessionId = s.uniqueId and s.sessionId = :s and r.studentId = :st " +
				"order by s.meetingDate desc, s.startPeriod desc", Object[].class).setParameter("s", sessionId).setParameter("st", student.getUniqueId()).list();
		int[] all = new int[4];
		for (Object[] o: rows) {
			AttendanceSession s = (AttendanceSession)o[0]; AttendanceRecord r = (AttendanceRecord)o[1];
			Class_ c = hib.get(Class_.class, s.getClassId());
			RecordInfo ri = new RecordInfo();
			ri.className = c.getClassLabel(); ri.date = AttendanceService.DATE.format(s.getMeetingDate()); ri.time = AttendanceService.time(s.getStartPeriod(), s.getStopPeriod());
			ri.room = AttendanceService.location(hib, sessionId, s.getLocationPermId()); ri.status = r.getStatus(); ri.source = r.getSource();
			response.records.add(ri);
			if (!"FINALISED".equals(s.getStatus())) continue;
			int[] cnt = perClass.get(s.getClassId());
			if (cnt == null) { cnt = new int[4]; perClass.put(s.getClassId(), cnt); }
			cnt[AttendanceService.index(r.getStatus())]++;
			all[AttendanceService.index(r.getStatus())]++;
		}
		for (Map.Entry<Long, int[]> e: perClass.entrySet()) {
			Class_ c = hib.get(Class_.class, e.getKey());
			int[] cnt = e.getValue();
			ClassStat cs = new ClassStat();
			cs.classId = c.getUniqueId(); cs.className = c.getClassLabel(); cs.instructors = AttendanceService.instructorNames(c);
			try { cs.course = c.getSchedulingSubpart().getControllingCourseOffering().getCourseName(); } catch (Exception ex) {}
			cs.present = cnt[0]; cs.late = cnt[1]; cs.absent = cnt[2]; cs.excused = cnt[3]; cs.sessions = cnt[0] + cnt[1] + cnt[2] + cnt[3];
			cs.ratePct = Math.round(10.0 * AttendanceService.rate(cfg, cnt)) / 10.0;
			cs.standing = AttendanceService.standing(cfg, cnt);
			response.classes.add(cs);
			if ("ELIGIBILITY_RISK".equals(cs.standing))
				response.warnings.add(cs.className + ": your attendance is " + cs.ratePct + "%, below the " + cfg.eligibilityPct + "% needed for examination eligibility.");
			else if ("BELOW_MIN".equals(cs.standing))
				response.warnings.add(cs.className + ": your attendance is " + cs.ratePct + "%, below the minimum of " + cfg.minPct + "%.");
		}
		Collections.sort(response.classes, new Comparator<ClassStat>() { @Override public int compare(ClassStat a, ClassStat b) { return Double.compare(a.ratePct, b.ratePct); } });
		response.overallPct = Math.round(10.0 * AttendanceService.rate(cfg, all)) / 10.0;
		for (AttendanceExcuse e: hib.createQuery("from AttendanceExcuse where sessionId = :s and studentId = :st order by submittedTs desc", AttendanceExcuse.class)
				.setParameter("s", sessionId).setParameter("st", student.getUniqueId()).list())
			response.excuses.add(AttendanceBackend.toExcuse(hib, e, false));
		return response;
	}
}
