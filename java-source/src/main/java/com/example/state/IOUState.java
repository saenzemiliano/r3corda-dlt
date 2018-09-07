package com.example.state;

import com.example.schema.IOUSchemaV2;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;

import java.util.Arrays;
import java.util.List;

/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
public class IOUState implements LinearState, QueryableState {
    private final Integer value;
    private final Long date;
    private final Party viewer;
    private final Party lender;
    private final Party borrower;
    private final UniqueIdentifier linearId;

    /**
     * @param value the value of the IOU.
     * @param viewer the party observing the IOUs.
     * @param lender the party issuing the IOU.
     * @param borrower the party receiving and approving the IOU.
     */
    public IOUState(Integer value,
                    Long date,
                    Party viewer,
                    Party lender,
                    Party borrower,
                    UniqueIdentifier linearId)
    {
        this.value = value;
        this.date = date;
        this.viewer =  viewer;
        this.lender = lender;
        this.borrower = borrower;
        this.linearId = linearId;
    }

    public Integer getValue() { return value; }
    public Long getDate() { return  date; }
    public Party getViewer() { return viewer; }
    public Party getLender() { return lender; }
    public Party getBorrower() { return borrower; }
    @Override public UniqueIdentifier getLinearId() { return linearId; }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(viewer, lender, borrower);
    }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof IOUSchemaV2) {
            return new IOUSchemaV2.PersistentIOU(
                    this.viewer.getName().toString(),
                    this.lender.getName().toString(),
                    this.borrower.getName().toString(),
                    this.value,
                    this.date,
                    this.linearId.getId());
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new IOUSchemaV2());
    }

    @Override
    public String toString() {
        return String.format("IOUState(value=%s, date=%s, viewer=%s, lender=%s, borrower=%s, linearId=%s)", value, date, viewer, lender, borrower, linearId);
    }
}