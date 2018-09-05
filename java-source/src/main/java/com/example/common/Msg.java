package com.example.common;

import net.corda.core.identity.Party;

import java.io.Serializable;

public class Msg implements Serializable {
    private String message;

    public Msg(String msg) {
        this.message = msg;
    }


    public void setMessage(String msg) {
        this.message = msg;
    }
    public String getMessage() {
        return this.message;
    }
}
