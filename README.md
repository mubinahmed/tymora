<!-- 
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
 -->
# UniTime

Comprehensive University Timetabling System
<https://www.unitime.org>

UniTime is a comprehensive educational scheduling system that supports developing
course and exam timetables, managing changes to these timetables, sharing rooms
with other events, and scheduling students to individual classes.
It is a distributed system that allows multiple university and departmental schedule managers
to coordinate efforts to build and modify a schedule that meets their diverse organizational
needs while allowing for minimization of student course conflicts. It can be used alone to
create and maintain a school's schedule of classes and/or exams, or interfaced with
an existing student information system. 

The system was originally developed as a collaborative effort by faculty,
students, and staff at universities in North America and Europe. The software
is distributed free under an open source license in hopes that other colleges
and universities can benefit their students through better scheduling or wish to
contribute to ongoing research in this area. The UniTime project has become
a sponsored project of the [Apereo Foundation][apereo] in March 2015.

### Components
- [Course Timetabling & Management][courses]
- [Examination Timetabling][exams]
- [Event Management][events]
- [Student Scheduling][students]

### Tutorials
- [Installation Instructions][install]
- [Building UniTime][build]
- [Setting up UniTime in Eclipse][eclipse]
- [Customization][customization]
- [Localization][localization]

### Links
- [UniTime 4.6 documentation][docs]
- [Online Documentation][help]
- [Online Demo][demo]
- [Downloads][downloads]
- [Nightly Builds][builds]
- [XML Interfaces][xml]
- [Publications][publications]

[courses]: https://www.unitime.org/uct_courses.php
[exams]: https://www.unitime.org/uct_exams.php
[events]: https://www.unitime.org/uct_events.php
[students]: https://www.unitime.org/uct_students.php
[help]: https://help.unitime.org
[install]: https://help.unitime.org/installation
[demo]: https://demo.unitime.org
[builds]: https://builds.unitime.org
[xml]: https://www.unitime.org/uct_interfaces.php
[publications]: https://www.unitime.org/publications.php
[downloads]: https://sourceforge.net/projects/unitime/files
[build]: https://help.unitime.org/building-unitime
[eclipse]: https://help.unitime.org/eclipse
[docs]: https://bit.ly/unitime46docs
[apereo]: https://www.apereo.org
[customization]: https://help.unitime.org/customizations
[localization]: https://help.unitime.org/localization

ChatGPT Summary:
- Incoming requests flow through the filters in WebContent/WEB-INF/web.xml (encoding, Hibernate session, Spring Security, PageAccess, etc.), then the Struts 2 StrutsPrepareAndExecuteFilter handles *.action while separate      
  servlets handle RPC/static endpoints (*.gwt via GwtDispatcherServlet, /unitime/gwt.rpc via GwtRpcServlet, exports, uploads, etc.).
- Struts is configured via WebContent/WEB-INF/struts.xml to use the convention plugin (scans .../action packages for @Action classes) and Tiles. Each action returns a named result that maps to a Tiles definition; e.g.,        
  JavaSource/org/unitime/timetable/action/RoleListAction.java returns selectPrimaryRole.tiles, which points at /selectPrimaryRole.jsp.
- Tiles definitions live in WebContent/WEB-INF/tiles.xml; all pages extend baseLayout, which renders through WebContent/layouts/layout-struts2.jsp. That JSP wraps every page with the common header/footer, menu placeholders,   
  localization setup, and includes CSS plus the GWT bootstrap script unitime/unitime.nocache.js; the requested page body is injected via <tiles:insertAttribute name="body"/>.
- GWT-driven pages use the same layout: the body is often /gwt2.jsp (JavaSource/org/unitime/timetable/action/GwtAction.java), which only contains the UniTimeGWT:Body and UniTimeGWT:Loading anchors. The GWT module JavaSource/  
  org/unitime/timetable/gwt/UniTime.gwt.xml is compiled (via the Maven GWT plugin) into target/gwt/unitime/... and served as unitime/unitime.nocache.js. The client entry point JavaSource/org/unitime/timetable/gwt/client/      
  Client.java attaches to those anchors, builds the UI, and talks back to the server through the RPC servlets defined in web.xml.
- Non-GWT pages work similarly but render server-side JSPs (using Struts tags and UniTime custom tags) as the body attribute while still inheriting the same layout for menus, navigation, and footer.

─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• - Incoming requests are protected by Spring Security (see WebContent/WEB-INF/securityContext.xml imported from applicationContext.xml). URL rules allow anonymous access to login/home/GWT entry points but require authentication
for *.action/*.do; the Spring DelegatingFilterProxy (springSecurityFilterChain in web.xml) enforces this. Form login uses UniTimeAuthenticationSuccessHandler/FailureHandler and redirects to selectPrimaryRole.action on       
success. Multiple auth providers are wired (database UniTimeUserDetailService + password encoder, optional JAAS/LDAP/CAS/OAuth variants via other securityContext*.xml files).
- After authentication, RoleListAction (JavaSource/org/unitime/timetable/action/RoleListAction.java) makes the user choose a “current authority” (role + academic session qualifier). The chosen UserAuthority is stored in the   
  UserContext and drives what rights are available; switching roles resets session attributes.
- Rights are enumerated centrally in JavaSource/org/unitime/timetable/security/rights/Right.java (hundreds of domain-scoped capabilities). Each right may declare an expected target type (e.g., InstructionalOffering, Session,  
  Department).
- Authorization checks are centralized in JavaSource/org/unitime/timetable/security/evaluation/UniTimePermissionCheck.java, exposed via SessionContext (HttpSessionContext). Checks first ensure the current authority grants     
  the requested Right, then resolve the target object/qualifier (with fallbacks like current session or the user’s departments/solver groups), and finally delegate to a Permission bean named permission<RightName> for domain-  
  specific logic. If no bean applies, generic session/department checks are used. checkPermission* throws AccessDeniedException; hasPermission* returns boolean.
- Spring Security’s expression and web handlers (UniTimeSecurityExpressionHandler, UniTimePermissionEvaluator, UniTimePermissionEvaluatorNoException) plug this permission check into both method-level security and JSP/GWT/     
  Spring expression usage. The custom ANONYMOUS filter is disabled; PageAccessFilter wraps every request to log slow pages and convert permission failures into a redirect to loginRequired.action.
- Struts/GWT code uses these checks everywhere: Struts actions call sessionContext.checkPermission(...), server-side GWT backends do the same, and JSP/custom tags often rely on SessionContext or Spring Security expressions to
  hide/disable UI based on Right membership.

In short: authenticate via Spring Security → pick a current authority (role + session) → all pages/actions call SessionContext permission checks backed by the Right enum and per-right Permission beans; denials surface as      
redirects to loginRequired.                                                                                                                                                                                                       
─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• Buttons and other UI affordances are guarded in two layers:

- JSP pages wrap button markup in Spring Security tags. The sec:authorize access="hasPermission(...)" tag uses the project’s UniTimePermissionEvaluator to check Right values before rendering the button at all.                 
  Examples: WebContent/user/classDetail.jsp hides Edit/Prefs/Assign buttons unless hasPermission(#form.classId, 'Class_', 'ClassEdit') or related rights; WebContent/admin/departmentEdit.jsp only shows “Delete” when            
  hasPermission(#form.id, 'Department', 'DepartmentDelete'); WebContent/admin/sessionList.jsp only shows “Add Session” when hasPermission(null, null, 'AcademicSessionAdd'). Because the permission check runs server-side,       
  unauthorized users never see those buttons.
- GWT pages don’t use JSP tags; instead the server sets capability flags in RPC responses based on the same SessionContext.hasPermission checks. For example, CurriculaServlet sets ci.setEditable(...), ReservationServlet sets  
  r.setEditable(...), and SectioningServlet fills permission booleans like canRegister, canEmail, setCanShowExternalId that the client uses to enable/disable buttons. The helper GwtRpcHelper funnels these permission checks    
  to UniTimePermissionCheck.

In both cases the Right enum plus the per-right Permission beans decide the outcome; buttons are either not rendered (JSP) or disabled/hidden (GWT) according to those permission results.                                        

───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
• To allow someone to create Rooms you need to give them the Right named AddRoom. In the SQL scripts this is wired like this:

- The permissions live in table rights (role_id, value). In Documentation/Database/MySQL/blank-data.sql you can see AddRoom added for the Sysadmin role:         
  insert into rights values (1,'AddRoom'), ... (role 1 is defined in the same file as Sysadmin). The same pattern appears in the “Default Permissions”/“System   
  Administrator” change scripts (e.g., Documentation/Database/MySQL/Changes/087 Default Permissions.sql and .../090 System Administrator.sql insert (@r1,        
  'AddRoom') for the chosen role).
- Users don’t get rights directly; they get roles via tmtbl_mgr_to_roles (manager_id, role_id,…). Example in blank-data.sql: manager 470 is linked to role 1     
  (Sysadmin) which already has AddRoom.

So to grant “create room” access for a user:

1. Ensure the role you want to use carries AddRoom (add a row into rights for that role_id if it doesn’t already):                                               
   insert into rights (role_id, value) select role_id, 'AddRoom' from roles where reference = 'Dept Sched Mgr';
2. Assign that role to the user’s timetable manager record in tmtbl_mgr_to_roles:                                                                                
   insert into tmtbl_mgr_to_roles (timetable_mgr_id, role_id, manager_id, receive_emails, visible) values (<mgr_id>, <role_id>, <mgr_id>, 1, 1);

Once the user’s manager is linked to a role that has AddRoom, the UI will allow room creation.                                                                   
