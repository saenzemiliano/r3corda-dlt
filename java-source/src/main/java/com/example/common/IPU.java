package com.example.common;

import net.corda.core.identity.Party;

public class IPU {

    public IPU(Party payer, Party loaner, int value) {
        this.loaner = loaner;
        this.payer = payer;
        this.value = value;
    }

    public final Party payer;
    public final Party loaner;
    public final int value;
}
