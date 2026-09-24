package org.dataone.portal.servlets;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;

/**
 * Registers newly logged-in users with the CN identity service.
 */
public class AccountRegistration {

    private static Log log = LogFactory.getLog(AccountRegistration.class);

    private AccountRegistration() {
    }

    /**
     * Register the subject with the CN if it isn't already registered. Failures are logged, not
     * thrown: login should still succeed if the CN can't be reached.
     */
    public static void registerIfNeeded(String subjectValue, String givenName, String familyName) {
        try {
            Subject subject = new Subject();
            subject.setValue(subjectValue);
            Person person = new Person();
            person.setSubject(subject);
            person.addGivenName(givenName);
            person.setFamilyName(familyName);
            try {
                D1Client.getCN().getSubjectInfo(null, subject);
            } catch (NotFound nf) {
                // so register them
                D1Client.getCN().registerAccount(null, person);
            }
        } catch (BaseException be) {
            // oh well, didn't register it, or something went wrong
            log.warn(be.getMessage(), be);
        }
    }
}
