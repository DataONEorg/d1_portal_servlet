package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dataone.configuration.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RedirectTargets}.
 */
public class RedirectTargetsTest {

    private String[] original;

    @BeforeEach
    public void setUp() {
        original = Settings.getConfiguration().getStringArray(RedirectTargets.ALLOWLIST_KEY);
        Settings.getConfiguration().setProperty(RedirectTargets.ALLOWLIST_KEY,
                                                "dataone.org, .arcticdata.io");
    }

    @AfterEach
    public void tearDown() {
        Settings.getConfiguration().clearProperty(RedirectTargets.ALLOWLIST_KEY);
        for (String value : original) {
            Settings.getConfiguration().addProperty(RedirectTargets.ALLOWLIST_KEY, value);
        }
    }

    @Test
    public void testAllowsListedHostsAndSubdomains() {
        assertTrue(RedirectTargets.isAllowed("https://dataone.org/"));
        assertTrue(RedirectTargets.isAllowed("https://search.dataone.org/profile?x=1"));
        assertTrue(RedirectTargets.isAllowed("https://arcticdata.io/catalog/"));
        assertTrue(RedirectTargets.isAllowed("https://SEARCH.DataONE.org/"));
    }

    @Test
    public void testRejectsOtherHosts() {
        assertFalse(RedirectTargets.isAllowed("https://evil.example/"));
        assertFalse(RedirectTargets.isAllowed("https://evildataone.org/"));
        assertFalse(RedirectTargets.isAllowed("https://dataone.org.evil.example/"));
        assertFalse(RedirectTargets.isAllowed("https://dataone.org@evil.example/"));
    }

    @Test
    public void testRejectsNonHttpsAndMalformedTargets() {
        assertFalse(RedirectTargets.isAllowed(null));
        assertFalse(RedirectTargets.isAllowed(""));
        assertFalse(RedirectTargets.isAllowed("http://search.dataone.org/"));
        assertFalse(RedirectTargets.isAllowed("javascript:alert(1)"));
        assertFalse(RedirectTargets.isAllowed("//search.dataone.org/"));
        assertFalse(RedirectTargets.isAllowed("/relative/path"));
        assertFalse(RedirectTargets.isAllowed("https://search.dataone.org/bad path"));
    }

    @Test
    public void testAllowsAnyHttpsHostWhenNotConfigured() {
        Settings.getConfiguration().clearProperty(RedirectTargets.ALLOWLIST_KEY);

        assertTrue(RedirectTargets.isAllowed("https://any.example.org/"));
        assertFalse(RedirectTargets.isAllowed("http://any.example.org/"));
    }
}
