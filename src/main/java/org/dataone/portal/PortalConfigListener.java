package org.dataone.portal;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;

/**
 * Loads the portal configuration into {@link Settings} when the webapp starts, before any servlet
 * is initialized. Servlets such as OrcidOAuthServlet read their configuration from Settings in
 * init(), and TokenGenerator reads its signing key and certificate paths from Settings.
 */
public class PortalConfigListener implements ServletContextListener {

    /** Context param naming the portal properties file to add to Settings */
    public static final String PORTAL_PROPERTIES_FILE = "portal.properties.file";

    /** Context param for the CN base URL; overrides any value in the properties file */
    public static final String CN_URL = "D1Client.CN_URL";

    /** Context param naming the oa4mp client configuration file used for portal certificates */
    public static final String OA4MP_CONFIG_FILE = "oa4mp:client.config.file";

    private static Log log = LogFactory.getLog(PortalConfigListener.class);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();

        // augment the properties with configured portal properties file
        String propertiesFile = context.getInitParameter(PORTAL_PROPERTIES_FILE);
        if (propertiesFile != null) {
            try {
                Settings.augmentConfiguration(propertiesFile);
            } catch (ConfigurationException e) {
                // fail startup rather than run without ORCID or token signing configuration
                log.error("Could not load portal properties from " + propertiesFile, e);
                throw new IllegalStateException(
                    "Could not load portal properties from " + propertiesFile, e);
            }
        }

        // these will override values specified in the properties file above
        String cnURL = context.getInitParameter(CN_URL);
        if (cnURL != null) {
            Settings.getConfiguration().setProperty(CN_URL, cnURL);
        }

        // point to the correct client config file
        // TODO: remove with the CILogon certificate-cookie code (d1_portal_servlet#11)
        String configFile = context.getInitParameter(OA4MP_CONFIG_FILE);
        if (configFile != null) {
            PortalCertificateManager.getInstance().setConfigFile(configFile);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // close the log handlers for uiuc
        // TODO: remove with the CILogon certificate-cookie code (d1_portal_servlet#11)
        try {
            PortalCertificateManager.getInstance().closeLoggers();
        } catch (Exception e) {
            // all we can do is complain
            log.error("Could not shutdown the UIUC loggers", e);
        }
    }
}
