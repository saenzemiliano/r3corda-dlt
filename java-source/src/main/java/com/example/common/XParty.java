package com.example.common;

import com.example.state.IOUState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;

import java.util.List;

public class XParty {


    public static boolean equal(Party partA, Party partyB) {
        return partA.getName().toString().equalsIgnoreCase(partyB.getName().toString());
    }

    public static boolean distinct(Party partA, Party partyB) {
        return !equal(partA, partyB);
    }
}
