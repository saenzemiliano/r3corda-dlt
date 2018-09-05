package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/**
 * An IPUState schema.
 */
public class IPUSchemaV2 extends MappedSchema {
    public IPUSchemaV2() {
        super(IPUSchema.class, 1, ImmutableList.of(PersistentIPU.class));
    }

    @Entity
    @Table(name = "iou_states")
    public static class PersistentIPU extends PersistentState {
        @Column(name = "viewer") private final String viewer;
        @Column(name = "payer") private final String payer;
        @Column(name = "loaner") private final String loaner;
        @Column(name = "value") private final int value;
        @Column(name = "linear_id") private final UUID linearId;


        public PersistentIPU(String viewer, String payer, String loaner, int value, UUID linearId) {
            this.viewer = viewer;
            this.payer = payer;
            this.loaner = loaner;
            this.value = value;
            this.linearId = linearId;
        }

        // Default constructor required by hibernate.
        public PersistentIPU() {
            this.viewer = null;
            this.payer = null;
            this.loaner = null;
            this.value = 0;
            this.linearId = null;
        }

        public String getViewer() {
            return viewer;
        }

        public String getPayer() {
            return payer;
        }

        public String getLoaner() {
            return loaner;
        }

        public int getValue() {
            return value;
        }

        public UUID getId() {
            return linearId;
        }
    }
}