package de.fi.first.business;

import de.fi.first.entity.Person;
import org.springframework.stereotype.Service;


@Service
public class BlacklistService {

    public boolean isBlacklisted(Person possibleBlacklistedPerson) {
        return false;
    }
}
