/*
 * Angular migration: lists the users an admin may masquerade as (the timetable
 * managers), reusing TimetableManager.getManagerList() — same source as the
 * legacy chameleon screen. Read-only; gated by Right.Chameleon.
 */
package org.unitime.timetable.rest.chameleon;

import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.rest.chameleon.ChameleonInterface.ChameleonUserItem;
import org.unitime.timetable.rest.chameleon.ChameleonInterface.GetChameleonUsersRequest;
import org.unitime.timetable.rest.chameleon.ChameleonInterface.GetChameleonUsersResponse;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.UserContext;
import org.unitime.timetable.security.rights.Right;

@GwtRpcImplements(GetChameleonUsersRequest.class)
public class GetChameleonUsersBackend implements GwtRpcImplementation<GetChameleonUsersRequest, GetChameleonUsersResponse> {

	@Override
	public GetChameleonUsersResponse execute(GetChameleonUsersRequest request, SessionContext context) {
		// A user who is already masquerading may open this screen (to stop or switch)
		// even without the Chameleon right — matches UserInfoBackend/ChameleonAction,
		// which treat "is a Chameleon" as sufficient. Otherwise the right is required.
		UserContext user = context.getUser();
		boolean masquerading = (user instanceof UserContext.Chameleon);
		if (!masquerading)
			context.checkPermission(Right.Chameleon);

		GetChameleonUsersResponse response = new GetChameleonUsersResponse();
		response.setMasquerading(masquerading);
		if (user != null)
			response.setCurrentName(user.getName());

		for (TimetableManager m : TimetableManager.getManagerList())
			if (m.getExternalUniqueId() != null)
				response.addUser(new ChameleonUserItem(m.getExternalUniqueId(), m.getName()));

		return response;
	}
}
