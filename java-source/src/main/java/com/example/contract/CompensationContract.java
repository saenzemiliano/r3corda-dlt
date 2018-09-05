package com.example.contract;

import com.example.state.IOUState;
import com.example.state.IPUState;
import com.example.state.IPUState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IPUState], which in turn encapsulates an [IPU].
 *
 * For a new [IPU] to be issued onto the ledger, a transaction is required which takes:
 * - At least one input state [IPU].
 * - Only one output state: the new [IPU].
 * - An Create() command with the public keys of both the lender, borrower and the viewer.
 *
 * All contracts must sub-class the [Contract] interface.
 */
public class CompensationContract implements Contract {
    public static final String IPU_CONTRACT_ID = "com.example.contract.CompensationContract";

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<Commands.Create> command = requireSingleCommand(tx.getCommands(), Commands.Create.class);
        requireThat(require -> {
            // Generic constraints around the IPU transaction.
            require.using("One input should be at least consumed when issuing an IPU.",
                    tx.getInputs().size() >= 1);
            require.using("Only one output state should be created.",
                    tx.getOutputs().size() == 1);

            final List<IOUState> inputs = tx.inputsOfType(IOUState.class);
            final List<IPUState> outputs = tx.outputsOfType(IPUState.class);
            final IPUState out = outputs.get(0);


            require.using("In input IPUs the viewer, lender and the borrower cannot be the same entity.",
                    inputs.stream().map(x -> x.getLender() != x.getViewer() && x.getViewer() != x.getBorrower() && x.getLender() != x.getBorrower()).reduce(true, (Boolean a, Boolean b) -> a && b));

            require.using("In output IPUs the viewer, lender and the borrower cannot be the same entity.",
                    outputs.stream().map(x -> x.getLoaner() != x.getViewer() && x.getViewer() != x.getPayer() && x.getLoaner() != x.getPayer()).reduce(true, (Boolean a, Boolean b) -> a && b));

            require.using("All of the viewers must be the same entity.",
                    inputs.stream().map(x -> x.getViewer() == out.getViewer()).reduce(true, (Boolean a, Boolean b) -> a && b));

            require.using("All of the input participants must be equal to output participants.",
                    inputs.stream().map(x -> (x.getLender() == out.getLoaner() || x.getLender() == out.getPayer()) && (x.getBorrower() == out.getLoaner() || x.getBorrower() == out.getPayer())).reduce(true, (Boolean a, Boolean b) -> a && b));

            require.using("All of the participants must be signers.",
                    command.getSigners().containsAll(out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

            require.using("The input IPU's value must be non-negative.",
                    inputs.stream().map(x -> x.getValue() >= 0).reduce(true, (Boolean a, Boolean b) -> a && b));

            require.using("The output IPU's value must be non-negative.",
                    outputs.stream().map(x -> x.getValue() >= 0).reduce(true, (Boolean a, Boolean b) -> a && b));

            /*
             *  Compensation Check
             */
            Boolean isOKCompensation;
            final Party PartyLoaner =  out.getLoaner();
            final Party PartyPayer =  out.getPayer();
            Integer debtLoaner =  inputs.stream().filter(x -> x.getLender() == PartyPayer).map(x -> x.getValue()).reduce(0, (Integer a, Integer b) -> (a + b));
            Integer debtPayer =  inputs.stream().filter(x -> x.getLender() == PartyLoaner).map(x -> x.getValue()).reduce(0, (Integer a, Integer b) -> (a + b));
            Integer amount = Math.abs(debtPayer - debtLoaner);

            if(debtPayer < debtLoaner) {
                isOKCompensation = false;
            } else {
                isOKCompensation = amount == out.getValue();
            }

            require.using("The compensation is not valid.", isOKCompensation);

            return null;
        });


    }


    /**
     * This contract only implements one command, Create.
     */
    public interface Commands extends CommandData {
        class Create implements Commands {}
    }
}