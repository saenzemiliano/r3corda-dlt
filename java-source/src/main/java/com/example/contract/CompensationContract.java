package com.example.contract;

import com.example.common.XParty;
import com.example.common.XUtils;
import com.example.state.IOUState;
import com.example.state.IPUState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.Requirements;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;

import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;


/**
 * A implementation of a basic smart contract in Corda.
 * All contracts must sub-class the [Contract] interface.
 */
public class CompensationContract implements Contract  {
    public static final String COMPENSATION_CONTRACT_ID = "com.example.contract.CompensationContract";



    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    @Override
    public void verify(LedgerTransaction tx) {
        requireThat(require -> {
            final List<CommandWithParties<CommandData>> commandWithParties = tx.getCommands();
            for (CommandWithParties<CommandData> commandDataCommandWithParties : commandWithParties) {
                if( commandDataCommandWithParties.getValue() instanceof  Commands.Create) {
                    ExecuteRulesCreateCommand(require, tx);
                } else if( commandDataCommandWithParties.getValue() instanceof  Commands.Compensate) {
                    ExecuteRulesCompensateCommand(require, tx);
                } else {
                    InvalidCommand(require, tx);
                }
            }
            return null;
        });
    }


    /**
     * This contract implements next commands:  Create and Compensate.
     */
    public interface Commands extends CommandData {
        class Create implements Commands {}
        class Compensate implements Commands {}
    }



    /**
     * Implements Commands Rules.
     */

    /**
     * Unknown Command
     */
    private void InvalidCommand(Requirements require, LedgerTransaction tx) {

        require.using("Invalid contract command in TX "+ tx.getId() +"\n", false);
    }

    /**
     * Compensate Command
     */
    private void ExecuteRulesCompensateCommand(final Requirements require, final LedgerTransaction tx) {
        final CommandWithParties<Commands.Compensate> command = requireSingleCommand(tx.getCommands(), Commands.Compensate.class);
        final List<IOUState> inputs = tx.inputsOfType(IOUState.class);
        final List<IPUState> outputs = tx.outputsOfType(IPUState.class);

        // Generic constraints around the IPU transaction.
        require.using("One input should be at least consumed when issuing an IPU.",
                inputs.size() >= 1);
        require.using("Only one output state should be created.",
                outputs.size() == 1);
        require.using("There are invalid outputs state.",
                tx.getOutputs().size() == outputs.size());

        require.using("There are invalid inputs state.",
                tx.getInputs().size() == inputs.size());

        final IPUState out = outputs.get(0);

        require.using("All of the participants must be signers.",
                command.getSigners().containsAll(out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

        // IPU-specific constraints.
        require.using("In input IPUs the viewer, lender and the borrower cannot be the same entity.",
                inputs.stream().map(x ->  XParty.distinct(x.getLender(), x.getViewer()) &&  XParty.distinct(x.getViewer(),x.getBorrower()) &&  XParty.distinct(x.getLender(), x.getBorrower())).reduce(true, (Boolean a, Boolean b) -> a && b));

        require.using("In output IPUs the viewer, lender and the borrower cannot be the same entity.",
                outputs.stream().map(x ->  XParty.distinct(x.getLoaner(), x.getViewer()) &&  XParty.distinct(x.getViewer(),x.getPayer()) &&  XParty.distinct(x.getLoaner(), x.getPayer())).reduce(true, (Boolean a, Boolean b) -> a && b));

        require.using("All of the viewers must be the same entity.",
                inputs.stream().map(x -> XParty.equal(x.getViewer(), out.getViewer())).reduce(true, (Boolean a, Boolean b) -> a && b));

        require.using("All of the input participants must be equal to output participants.",
                inputs.stream()
                        .map(x -> (XParty.equal(x.getLender(), out.getLoaner()) || XParty.equal(x.getLender(), out.getPayer()))
                                && (XParty.equal(x.getBorrower(), out.getLoaner()) || XParty.equal(x.getBorrower() , out.getPayer())))
                        .reduce(true, (Boolean a, Boolean b) -> a && b));

        require.using("All of the participants must be signers.",
                command.getSigners().containsAll(out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

        require.using("The input IOU's value must be non-negative.",
                inputs.stream().map(x -> x.getValue() >= 0).reduce(true, (Boolean a, Boolean b) -> a && b));

        require.using("The output IPU's value must be non-negative.",
                outputs.stream().map(x -> x.getValue() >= 0).reduce(true, (Boolean a, Boolean b) -> a && b));

        /*
         *  Compensation Check
         */
        Boolean isOKCompensation;
        final Party PartyLoaner =  out.getLoaner();
        final Party PartyPayer =  out.getPayer();
        Integer debtLoaner =  inputs.stream().filter(x -> XParty.equal(x.getLender(), PartyPayer)).map(x -> x.getValue()).reduce(0, (Integer a, Integer b) -> (a + b));
        Integer debtPayer =  inputs.stream().filter(x -> XParty.equal(x.getLender(), PartyLoaner)).map(x -> x.getValue()).reduce(0, (Integer a, Integer b) -> (a + b));
        Integer amount = Math.abs(debtPayer - debtLoaner);

        if(debtPayer < debtLoaner) {
            isOKCompensation = false;
        } else {
            isOKCompensation = amount == out.getValue();
        }

        require.using("The compensation is not valid.", isOKCompensation);
    }


    /**
     * Create Command
     */
    private void ExecuteRulesCreateCommand(final Requirements require, final LedgerTransaction tx) {
        final CommandWithParties<Commands.Create> command = requireSingleCommand(tx.getCommands(), Commands.Create.class);
        final List<IOUState> outputs = tx.outputsOfType(IOUState.class);


        require.using("No inputs should be consumed when issuing an IOU.",
                tx.getInputs().isEmpty());
        require.using("Only one output state should be created.",
                tx.getOutputs().size() == 1);
        require.using("There are invalid outputs state.",
                tx.getOutputs().size() == outputs.size());
        final IOUState out = tx.outputsOfType(IOUState.class).get(0);
        require.using("The lender and the borrower cannot be the same entity.",
                XParty.distinct(out.getLender(), out.getBorrower()));
        require.using("All of the participants must be signers.",
                command.getSigners().containsAll(out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));
        // IOU-specific constraints.
        require.using("The IOU's value must be non-negative.",
                out.getValue() > 0);
    }

}