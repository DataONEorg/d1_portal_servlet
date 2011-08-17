package org.cilogon.portal.servlets;

import edu.uiuc.ncsa.csd.util.MyLogger;
import org.cilogon.portal.config.cli.PortalConfigurationDepot;
import org.cilogon.portal.config.rdf.PortalRoot;
import org.cilogon.util.CILogonUriRefFactory;
import org.tupeloproject.kernel.OperatorException;
import org.tupeloproject.rdf.Resource;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import static org.cilogon.portal.servlets.ConfigConstants.CONFIG_FILE_NAME;
import static org.cilogon.portal.servlets.ConfigConstants.CONFIG_FILE_PATH;


/**
 * <p>Created by Jeff Gaynor<br>
 * on Jun 11, 2011 at  3:27:35 PM
 */
public class SetupUtil {

    static MyLogger logger;

    public static MyLogger getLogger() {
        if (logger == null) {
            logger = new MyLogger("setup.jsp");
        }
        return logger;
    }

    protected static String getParam(HttpServletRequest request, Resource key) {
        return request.getParameter(key.toString());
    }

    public static PortalRoot getConfig(ServletContext context) throws IOException, OperatorException {
        File f = new File(context.getRealPath(CONFIG_FILE_PATH + CONFIG_FILE_NAME));
        //InputStream inputStream = context.getResourceAsStream(CONFIG_FILE_PATH + CONFIG_FILE_NAME);
        PortalConfigurationDepot portalConfigurationDepot = null;
        if (f.exists()) {
            portalConfigurationDepot = new PortalConfigurationDepot(f);
        } else {
            portalConfigurationDepot = new PortalConfigurationDepot();
        }
        if (portalConfigurationDepot.findRoot() == null) {
            portalConfigurationDepot.createRoot(CILogonUriRefFactory.uriRef());
        }
        return (PortalRoot) portalConfigurationDepot.getCurrentConfiguration();
    }

    public static void saveConfig(ServletContext context, PortalConfigurationDepot portalConfiguration) throws Exception {
        portalConfiguration.save(); // save it to the context before serializing!!!
        File f = new File(context.getRealPath(CONFIG_FILE_PATH + CONFIG_FILE_NAME));
        portalConfiguration.serialize(f);
    }

    public static void setupFileStore(HttpServletRequest request, HttpServletResponse response) {

    }
}
