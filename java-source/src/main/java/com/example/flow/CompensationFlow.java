package com.example.flow;
import com.example.common.XParty;
import com.example.contract.CompensationContract;
import co.paralleluniverse.fibers.Suspendable;
import com.example.state.IOUState;
import com.example.state.IPUState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.contract.CompensationContract.COMPENSATION_CONTRACT_ID;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IPU encapsulated
 * within an [IPUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IPU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the varipus stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
public class CompensationFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final List<StateAndRef<IOUState>> stateAndRefs;
        private final Party viewerParty;
        private final Party payerParty;
        private final Party loanerParty;
        private final int ipuValue;

        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new IPU.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(List<StateAndRef<IOUState>> stateAndRefs, Party viewerParty, Party payerParty, Party loanerParty, int ipuValue) {
            this.stateAndRefs = stateAndRefs;
            this.viewerParty = viewerParty;
            this.payerParty = payerParty;
            this.loanerParty = loanerParty;
            this.ipuValue = ipuValue;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            IPUState ipuState = new IPUState(ipuValue, System.currentTimeMillis(), viewerParty, payerParty, loanerParty, new UniqueIdentifier());
            final TransactionBuilder txBuilder = new TransactionBuilder(notary);
            final Command<CompensationContract.Commands.Compensate> txCommand = new Command<>(
                    new CompensationContract.Commands.Compensate(),
                    ImmutableList.of(viewerParty.getOwningKey(), payerParty.getOwningKey(), loanerParty.getOwningKey()));

            // Add IOUs
            for (StateAndRef<IOUState> stateAndRef : stateAndRefs) {
                txBuilder.addInputState(stateAndRef);
            }
            // Add IPU
            txBuilder.addOutputState(ipuState, COMPENSATION_CONTRACT_ID);
            txBuilder.addCommand(txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparties, and receive it back with their signature.
            SignedTransaction fullySignedTxTemp = null;
            if(XParty.equal(me, viewerParty)) {
                FlowSession loanerPartySession = initiateFlow(loanerParty);
                FlowSession payerPartSession = initiateFlow(payerParty);
                fullySignedTxTemp = subFlow(
                        new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(payerPartSession, loanerPartySession), CollectSignaturesFlow.Companion.tracker()));
            } else {
                FlowSession viewerPartySession = initiateFlow(viewerParty);
                FlowSession counterPartySession = initiateFlow((XParty.equal(me, payerParty) ? loanerParty : payerParty ));
                fullySignedTxTemp = subFlow(
                        new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(viewerPartySession, counterPartySession), CollectSignaturesFlow.Companion.tracker()));
            }
            final SignedTransaction fullySignedTx = fullySignedTxTemp;
            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final static Logger logger = Logger.getLogger(Acceptor.class.getName());

        private final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an IPU transaction.", output instanceof IPUState);
                        IPUState iou = (IPUState) output;
                        require.using("I won't accept IPUs with a value lower 0.", iou.getValue() >= 0);
                        return null;
                    });
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }
}
