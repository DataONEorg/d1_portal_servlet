package org.dataone.portal.servlets;

import edu.uiuc.ncsa.security.core.util.Benchmarker;
import edu.uiuc.ncsa.security.servlet.PresentableState;
import org.cilogon.portal.CILogonService;
import org.cilogon.portal.servlets.PresentableServlet;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

import static edu.uiuc.ncsa.security.servlet.JSPUtil.fwd;
import static org.cilogon.util.CILogon.CERT_REQUEST_ID;

/**
 * A very simple sample servlet showing how a portal can start delegation. This just does the
 * initial request then a redirect
 * so there is nothing to display to the user.
 * <p>Created by Jeff Gaynor<br>
 * on Jun 18, 2010 at  2:10:58 PM
 */
public class StartRequest extends PresentableServlet {

    protected void doIt(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        ServletState ss = newServletState(request, response);
        try {
            prepare(ss);
            Benchmarker bm = new Benchmarker(this);
            bm.msg("2.a. Starting request");
            String identifier = request.getParameter("identifier");
            if (identifier == null) {
                identifier = "id-" + System.nanoTime();
            }
            String target = request.getParameter("target");
        	if (target != null) {
            	request.getSession().setAttribute("target", target);
        	}
            // Set the cookie
            debug("2.a. Adding cookie for identifier = " + identifier);
            Cookie cookie = new Cookie(CERT_REQUEST_ID, identifier);
            response.addCookie(cookie);
            // Drumroll please: The actual work for this servlet.
            CILogonService cis = new CILogonService(getPortalEnvironment());
            URI redirectUri = cis.requestCredential(identifier);
            // Now that the work is done, we have to redirect the user to the authorization page
            bm.msg("2.d. Got redirect uri");
            present(ss);
            response.sendRedirect(redirectUri.toString());
        } catch (Throwable t) {
            handleException( t, request, response);
        }

    }

    public void prepare(PresentableState pState) throws Throwable {
    }

    public void present(PresentableState pState) throws Throwable {
    }

    public void handleError(PresentableState pState,
                            Throwable t) throws IOException, ServletException {
        fwd(pState.getRequest(),  pState.getResponse(), "/error.jsp");
    }
}