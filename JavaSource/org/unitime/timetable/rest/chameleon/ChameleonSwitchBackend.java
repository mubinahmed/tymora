/*
 * Angular migration: applies (or stops) a masquerade, replicating
 * ChameleonAction.doSwitch — clears session attributes, unwraps any current
 * masquerade, then either restores the original authentication (when switching
 * back to self) or installs a ChameleonAuthentication for the target user.
 * Gated by Right.Chameleon. Runs on the request thread, so the changed
 * SecurityContext is persisted to the session exactly as the legacy action.
 */
package org.unitime.timetable.rest.chameleon;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.unitime.timetable.action.ChameleonAction.ChameleonAuthentication;
import org.unitime.timetable.defaults.SessionAttribute;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseNull;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.rest.chameleon.ChameleonInterface.ChameleonSwitchRequest;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.UserContext;
import org.unitime.timetable.security.context.ChameleonUserContext;
import org.unitime.timetable.security.rights.Right;

@GwtRpcImplements(ChameleonSwitchRequest.class)
public class ChameleonSwitchBackend implements GwtRpcImplementation<ChameleonSwitchRequest, GwtRpcResponseNull> {

	@Override
	public GwtRpcResponseNull execute(ChameleonSwitchRequest request, SessionContext context) {
		// A user who is already masquerading may switch/stop without holding the
		// Chameleon right (matches ChameleonAction.execute); everyone else needs it.
		UserContext user = context.getUser();
		if (user instanceof UserContext.Chameleon)
			user = ((UserContext.Chameleon) user).getOriginalUserContext();
		else
			context.checkPermission(Right.Chameleon);

		// Clear per-session state carried over from the previous identity.
		for (SessionAttribute a : SessionAttribute.values())
			context.removeAttribute(a);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof ChameleonAuthentication)
			authentication = ((ChameleonAuthentication) authentication).getOriginalAuthentication();

		if (request.getPuid() == null || request.getPuid().isEmpty()
				|| (user != null && request.getPuid().equals(user.getExternalUserId()))) {
			// switch back to self
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} else {
			SecurityContextHolder.getContext().setAuthentication(
					new ChameleonAuthentication(authentication,
							new ChameleonUserContext(request.getPuid(), request.getName(), user)));
		}
		return new GwtRpcResponseNull();
	}
}
