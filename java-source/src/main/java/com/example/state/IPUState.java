package com.example.state;

import com.example.schema.IPUSchemaV2;
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
 * The state object recording IPU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
public class IPUState implements LinearState, QueryableState {
    private final Integer value;
    private final Long date;
    private final Party viewer;
    private final Party payer;
    private final Party loaner;
    private final UniqueIdentifier linearId;

    /**
     * @param value the value of the IPU.
     * @param viewer the party observing the IPUs.
     * @param payer the party issuing the IPU.
     * @param loaner the party receiving and approving the IPU.
     */
    public IPUState(Integer value,
                    Long date,
                    Party viewer,
                    Party payer,
                    Party loaner,
                    UniqueIdentifier linearId)
    {
        this.value = value;
        this.date = date;
        this.viewer =  viewer;
        this.payer = payer;
        this.loaner = loaner;
        this.linearId = linearId;
    }

    public Integer getValue() { return value; }
    public Long getDate() { return date; }
    public Party getViewer() { return viewer; }
    public Party getPayer() { return payer; }
    public Party getLoaner() { return loaner; }
    @Override public UniqueIdentifier getLinearId() { return linearId; }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(viewer, payer, loaner);
    }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof IPUSchemaV2) {
            return new IPUSchemaV2.PersistentIPU(
                    this.viewer.getName().toString(),
                    this.payer.getName().toString(),
                    this.loaner.getName().toString(),
                    this.value,
                    this.date,
                    this.linearId.getId());
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new IPUSchemaV2());
    }

    @Override
    public String toString() {
        return String.format("IPUState(value=%s, date=%s, viewer=%s, payer=%s, loaner=%s, linearId=%s)", value, date, viewer, payer, loaner, linearId);
    }
}