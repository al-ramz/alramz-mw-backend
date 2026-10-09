package com.alramz.service;

public interface DuplicateCheckService {

    boolean[] checkDuplicates(String nin, String eid, String email, String passport);
}
