package com.example.contract;

import com.example.state.IOUState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import static com.example.contract.CompensationContract.COMPENSATION_CONTRACT_ID;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class IOUContractTests {
    static private final MockServices ledgerServices = new MockServices();
    static private TestIdentity megaCorp = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
    static private TestIdentity miniCorp = new TestIdentity(new CordaX500Name("MiniCorp", "London", "GB"));
    static private TestIdentity viewerCorp = new TestIdentity(new CordaX500Name("ViewerCorp", "London", "GB"));

    @Test
    public void transactionMustIncludeCreateCommand() {
        Integer iou = 1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou, null, viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.fails();
                tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()), new CompensationContract.Commands.Create());
                tx.verifies();
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionMustHaveNoInputs() {
        Integer iou = 1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.input(COMPENSATION_CONTRACT_ID, new IOUState(iou, null, viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou, null,viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()), new CompensationContract.Commands.Create());
                tx.failsWith("No inputs should be consumed when issuing an IOU.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionMustHaveOneOutput() {
        Integer iou = 1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou,  null,viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou, null, viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()), new CompensationContract.Commands.Create());
                tx.failsWith("Only one output state should be created.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void lenderMustSignTransaction() {
        Integer iou = 1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou,  null,viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.command(miniCorp.getPublicKey(), new CompensationContract.Commands.Create());
                tx.failsWith("All of the participants must be signers.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void borrowerMustSignTransaction() {
        Integer iou = 1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou,  null,viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.command(megaCorp.getPublicKey(), new CompensationContract.Commands.Create());
                tx.failsWith("All of the participants must be signers.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void lenderIsNotBorrower() {
        Integer iou = 1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou,  null,viewerCorp.getParty(), megaCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()), new CompensationContract.Commands.Create());
                tx.failsWith("The lender and the borrower cannot be the same entity.");
                return null;
            });
            return null;
        }));
    }

    @Test
    public void cannotCreateNegativeValueIOUs() {
        Integer iou = -1;
        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COMPENSATION_CONTRACT_ID, new IOUState(iou, null, viewerCorp.getParty(), miniCorp.getParty(), megaCorp.getParty(), new UniqueIdentifier()));
                tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()), new CompensationContract.Commands.Create());
                tx.failsWith("The IOU's value must be non-negative.");
                return null;
            });
            return null;
        }));
    }
}