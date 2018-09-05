package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.*;
import java.util.Date;
import java.util.UUID;

/**
 * An IOUState schema.
 */
public class IOUSchemaV2 extends MappedSchema {
    public IOUSchemaV2() {
        super(IOUSchema.class, 1, ImmutableList.of(PersistentIOU.class));
    }

    @Entity
    @Table(name = "iou_states")
    public static class PersistentIOU extends PersistentState {
        @Column(name = "viewer") private final String viewer;
        @Column(name = "lender") private final String lender;
        @Column(name = "borrower") private final String borrower;
        @Column(name = "value") private final int value;
        @Column(name = "date_create") @Temporal(TemporalType.TIMESTAMP) private final Date date;
        @Column(name = "linear_id") private final UUID linearId;


        public PersistentIOU(String viewer, String lender, String borrower, int value, Date date, UUID linearId) {
            this.viewer = viewer;
            this.lender = lender;
            this.borrower = borrower;
            this.value = value;
            this.date = date;
            this.linearId = linearId;
        }

        // Default constructor required by hibernate.
        public PersistentIOU() {
            this.viewer = null;
            this.lender = null;
            this.borrower = null;
            this.value = 0;
            this.date = null;
            this.linearId = null;
        }

        public String getViewer() {
            return viewer;
        }

        public String getLender() {
            return lender;
        }

        public String getBorrower() {
            return borrower;
        }

        public int getValue() {
            return value;
        }

        public Date getDate() {
            return date;
        }

        public UUID getId() {
            return linearId;
        }
    }
}