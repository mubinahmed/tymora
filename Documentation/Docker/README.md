## How to use

```
cd docker
```

- Build and deploy docker images using the following command

```
docker-compose build && docker-compose up
```

- Once the project has been built and deployed, Tymora should become available at [localhost:8888](http://localhost:8888)

## Demo users

You can enter the demo as one of the following users. In every case the **password is same as the user name**.

| User | Role | What they can do |
|---|---|---|
| `guest` | View-only | Can see all course data (for all departments) and all committed timetabling solutions, but cannot change anything. |
| `dept` | Schedule manager | Can edit course data of their department and work with the appropriate timetabling solutions. Classes that are externally managed (e.g. large-lecture-room classes timetabled centrally by another department) are only editable by this user during the **input data entry** phase; afterwards ownership transfers to the appropriate manager. In the current example, all subject areas are managed by the same department. |
| `llr` | Large lecture room manager | Can edit all classes marked as large-lecture-room classes and work with large-lecture-room timetables. |
| `exam` | Examination manager | Can edit all examination data and solutions. |
| `event` | Event manager | Can edit, approve, or reject all special and course events. |
| `admin` | Administrator | Can edit all course data and timetabling solutions. Many administrative pages are also available only to this role. |
| `doe`, `newman`, `smith` | Instructors | Associated with instructors in the system. Can see their personalized instructor schedules and existing events, and request a new (special) event (which may need approval by an event manager or administrator). |
| `student` | Student | Associated with a student in the system. Can see their personalized student schedule and existing events, and request a new (special) event (which may need approval by an event manager or administrator). |

## Notes

- This is a simple installation with only one web-server and no dedicated solver server.

- All components (Java, Tomcat, and MySQL) are included in one container.

- No HTTPS included, but a reverse-proxy can be used to provide SSL layer.

