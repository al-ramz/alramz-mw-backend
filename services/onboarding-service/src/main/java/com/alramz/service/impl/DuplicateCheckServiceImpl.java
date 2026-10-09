package com.alramz.service.impl;

import com.alramz.logging.aspect.Loggable;
import com.alramz.service.DuplicateCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DuplicateCheckServiceImpl implements DuplicateCheckService {

    private final DuplicateCheckHelper duplicateCheckHelper;

    public DuplicateCheckServiceImpl(DuplicateCheckHelper duplicateCheckHelper) {
        this.duplicateCheckHelper = duplicateCheckHelper;
    }

    @Override
    @Loggable
    public boolean[] checkDuplicates(String nin, String eid, String email, String passport) {
        boolean ninExists = false;
        boolean eidExists = false;
        boolean emailExists = false;
        boolean passportExists = false;

        if (nin != null && !nin.isBlank()) {
            ninExists = duplicateCheckHelper.checkNinExists(nin);
        }
        if (eid != null && !eid.isBlank()) {
            eidExists = duplicateCheckHelper.checkEidExists(eid);
        }
        if (email != null && !email.isBlank()) {
            emailExists = duplicateCheckHelper.checkEmailExists(email);
        }
        if (passport != null && !passport.isBlank()) {
            passportExists = duplicateCheckHelper.checkPassportExists(passport);
        }

        return new boolean[]{ninExists, eidExists, emailExists, passportExists};
    }
}
