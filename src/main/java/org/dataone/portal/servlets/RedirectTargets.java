package org.dataone.portal.servlets;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;

/**
 * Checks the target URLs that the login and logout servlets redirect browsers to, so the portal
 * can't be used as an open redirect.
 * <p>
 * A target must be an absolute https URL. If the {@value #ALLOWLIST_KEY} setting lists host
 * suffixes (comma separated, e.g. {@code dataone.org, arcticdata.io}), the target's host must be
 * one of them or a subdomain of one. If the setting is empty, any https host is allowed and a
 * warning is logged.
 */
public class RedirectTargets {

    public static final String ALLOWLIST_KEY = "portal.redirect.allowlist";

    private static Log log = LogFactory.getLog(RedirectTargets.class);

    private RedirectTargets() {
    }

    /**
     * @param target the requested redirect target
     * @return true if the portal may redirect a browser to the target
     */
    public static boolean isAllowed(String target) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(target);
        } catch (URISyntaxException e) {
            return false;
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);

        List<String> allowed = getAllowlist();
        if (allowed.isEmpty()) {
            log.warn(ALLOWLIST_KEY + " is not configured; allowing redirect to " + host);
            return true;
        }
        for (String suffix : allowed) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        log.warn("Rejecting redirect to a host that is not in " + ALLOWLIST_KEY + ": " + host);
        return false;
    }

    private static List<String> getAllowlist() {
        List<String> allowed = new ArrayList<String>();
        // split on commas here: Settings may or may not have list delimiter parsing enabled
        for (String value : Settings.getConfiguration().getStringArray(ALLOWLIST_KEY)) {
            for (String entry : value.split(",")) {
                String suffix = entry.trim().toLowerCase(Locale.ROOT);
                if (suffix.startsWith(".")) {
                    suffix = suffix.substring(1);
                }
                if (!suffix.isEmpty()) {
                    allowed.add(suffix);
                }
            }
        }
        return allowed;
    }
}
