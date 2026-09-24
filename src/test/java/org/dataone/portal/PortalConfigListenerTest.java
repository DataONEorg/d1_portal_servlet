package org.dataone.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.dataone.configuration.Settings;
import org.dataone.portal.servlets.oauth.OrcidOAuthServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PortalConfigListener}.
 */
public class PortalConfigListenerTest {

    @TempDir
    Path tempDir;

    private ServletContextEvent eventFor(String propertiesFile, String cnUrl) {
        ServletContext context = mock(ServletContext.class);
        when(context.getInitParameter(PortalConfigListener.PORTAL_PROPERTIES_FILE))
            .thenReturn(propertiesFile);
        when(context.getInitParameter(PortalConfigListener.CN_URL)).thenReturn(cnUrl);
        return new ServletContextEvent(context);
    }

    private String writeProperties(String content) throws Exception {
        Path file = tempDir.resolve("portal.properties");
        Files.writeString(file, content);
        return file.toString();
    }

    @Test
    public void testContextInitialized_loadsPortalPropertiesAndCnUrl() throws Exception {
        String file = writeProperties("listener.test.key=listener-test-value\n");

        new PortalConfigListener()
            .contextInitialized(eventFor(file, "https://cn-test.example.org/cn"));

        assertEquals("listener-test-value",
                     Settings.getConfiguration().getString("listener.test.key"));
        assertEquals("https://cn-test.example.org/cn",
                     Settings.getConfiguration().getString(PortalConfigListener.CN_URL));
    }

    @Test
    public void testContextInitialized_failsOnMissingPropertiesFile() {
        String missing = tempDir.resolve("does-not-exist.properties").toString();

        assertThrows(IllegalStateException.class,
                     () -> new PortalConfigListener().contextInitialized(eventFor(missing, null)));
    }

    @Test
    public void testOrcidOAuthServletInit_readsSettingsLoadedByListener() throws Exception {
        String file = writeProperties("orcid.client.id=APP-FROM-LISTENER\n");
        new PortalConfigListener().contextInitialized(eventFor(file, null));

        new OrcidOAuthServlet().init(mock(ServletConfig.class));

        Field clientId = OrcidOAuthServlet.class.getDeclaredField("CLIENT_ID");
        clientId.setAccessible(true);
        assertEquals("APP-FROM-LISTENER", clientId.get(null));
    }
}
