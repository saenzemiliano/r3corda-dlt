package com.example.common;

import com.example.state.IOUState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;

import java.util.List;

public class XUtils {

    public static IPU compensate(final List<StateAndRef<IOUState>> stateAndRefs, final Party PartyA, final Party PartyB) {

        Integer debtB =  stateAndRefs.stream().filter(x -> XParty.equal(x.getState().getData().getLender(), PartyA)).map(x -> x.getState().getData().getValue()).reduce(0, (Integer a, Integer b) -> (a + b));
        Integer debtA =  stateAndRefs.stream().filter(x -> XParty.equal(x.getState().getData().getLender(), PartyB)).map(x -> x.getState().getData().getValue()).reduce(0, (Integer a, Integer b) -> (a + b));
        Integer amount = Math.abs(debtA - debtB);

        if(debtA < debtB) {
            return new IPU(PartyB, PartyA , amount);
        } else {
            return new IPU(PartyA, PartyB , amount);
        }
    }


}
