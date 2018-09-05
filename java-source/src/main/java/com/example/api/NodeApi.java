package com.example.api;

import com.example.common.IPU;
import com.example.common.XUtils;
import com.example.flow.CompensationFlow;
import com.example.flow.RegularFlow;
import com.example.state.IOUState;
import com.example.schema.IOUSchemaV2;
import com.example.state.IPUState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.*;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
public class NodeApi {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Notary");

    static private final Logger logger = LoggerFactory.getLogger(NodeApi.class);

    public NodeApi(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, CordaX500Name> whoami() {
        return ImmutableMap.of("me", myLegalName);
    }

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getPeers() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("peers", nodeInfoSnapshot
                .stream()
                .map(node -> node.getLegalIdentities().get(0).getName())
                .filter(name -> !name.equals(myLegalName) && !serviceNames.contains(name.getOrganisation()))
                .collect(toList()));
    }

    @GET
    @Path("corda-nodes")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<NodeInfo>> getAllNodes() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("nodes", nodeInfoSnapshot);
    }

    /**
     * Displays all IOU states that exist in the node's vault.
     */
    @GET
    @Path("ious")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<IOUState>> getIOUs() {
        return rpcOps.vaultQuery(IOUState.class).getStates();
    }

    /**
     * Displays all IPU states that exist in the node's vault.
     */
    @GET
    @Path("ipus")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<IPUState>> getIPUs() {
        return rpcOps.vaultQuery(IPUState.class).getStates();
    }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /api/example/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("create-iou")
    public Response createIOU(@QueryParam("iouValue") int iouValue, @QueryParam("viewerPartyName") CordaX500Name viewerPartyName, @QueryParam("otherPartyName") CordaX500Name otherPartyName) throws InterruptedException, ExecutionException {
        if (iouValue <= 0) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'iouValue' must be non-negative.\n");
        }
        if (otherPartyName == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'otherPartyName' missing or has wrong format.\n");
        }

        if (viewerPartyName == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'viewerPartyName' missing or has wrong format.\n");
        }

        final Party otherParty = rpcOps.wellKnownPartyFromX500Name(otherPartyName);
        if (otherParty == null) {
            return ResponseStatus(BAD_REQUEST, "Party named " + otherPartyName + "cannot be found.\n");
        }

        final Party viewerParty = rpcOps.wellKnownPartyFromX500Name(viewerPartyName);
        if (viewerParty == null) {
            return ResponseStatus(BAD_REQUEST, "Party named " + viewerPartyName + "cannot be found.\n");
        }

        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(RegularFlow.Initiator.class, iouValue, viewerParty, otherParty)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return ResponseStatus(CREATED, msg);

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return ResponseStatus(BAD_REQUEST, msg);
        }
    }
	
	/**
     * Displays all IOU states that are created by lender.
     */
    @GET
    @Path("ious-lender")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIOUsByLender(@QueryParam("lenderPartyName") CordaX500Name lenderPartyName) throws NoSuchFieldException {
        QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);
        Field lender = IOUSchemaV2.PersistentIOU.class.getDeclaredField("lender");
        CriteriaExpression lenderIndex = Builder.equal(lender, lenderPartyName.toString());
        QueryCriteria lenderCriteria = new QueryCriteria.VaultCustomQueryCriteria(lenderIndex);
        QueryCriteria criteria = generalCriteria.and(lenderCriteria);
        List<StateAndRef<IOUState>> results = rpcOps.vaultQueryByCriteria(criteria,IOUState.class).getStates();
        return Response.status(OK).entity(results).build();
    }

    /**
     * Displays all IOU states that are created by borrower.
     */
    @GET
    @Path("ious-borrower")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIOUsByBorrower(@QueryParam("borrowerPartyName") CordaX500Name borrowerPartyName) throws NoSuchFieldException {
        QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);
        Field borrower = IOUSchemaV2.PersistentIOU.class.getDeclaredField("borrower");
        CriteriaExpression borrowerIndex = Builder.equal(borrower, borrowerPartyName.toString());
        QueryCriteria borrowerCriteria = new QueryCriteria.VaultCustomQueryCriteria(borrowerIndex);
        QueryCriteria criteria = generalCriteria.and(borrowerCriteria);
        List<StateAndRef<IOUState>> results = rpcOps.vaultQueryByCriteria(criteria,IOUState.class).getStates();
        return Response.status(OK).entity(results).build();
    }

    /**
     * Displays all IOU states that are created by viewer.
     */
    @GET
    @Path("ious-viewer")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIOUsByViewer(@QueryParam("viewerPartyName") CordaX500Name viewerPartyName) throws NoSuchFieldException {
        QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);
        Field viewer = IOUSchemaV2.PersistentIOU.class.getDeclaredField("viewer");
        CriteriaExpression viewerIndex = Builder.equal(viewer, viewerPartyName.toString());
        QueryCriteria viewerCriteria = new QueryCriteria.VaultCustomQueryCriteria(viewerIndex);
        QueryCriteria criteria = generalCriteria.and(viewerCriteria);
        List<StateAndRef<IOUState>> results = rpcOps.vaultQueryByCriteria(criteria,IOUState.class).getStates();
        return Response.status(OK).entity(results).build();
    }

    private   List<StateAndRef<IOUState>>  getIOUsByPartiesAndDates_( CordaX500Name onePartyName,
                                                                      CordaX500Name anotherPartyName,
                                                                      Date from,
                                                                      Date to) throws Exception {

        final Party oneParty = rpcOps.wellKnownPartyFromX500Name(onePartyName);
        if (oneParty == null) {
            throw new Exception("Party named " + onePartyName + "cannot be found.\n");
        }
        final Party anotherParty = rpcOps.wellKnownPartyFromX500Name(anotherPartyName);
        if (anotherParty == null) {
            throw new Exception("Party named " + anotherPartyName + "cannot be found.\n");
        }

        QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);

        Field lenderField = IOUSchemaV2.PersistentIOU.class.getDeclaredField("lender");
        CriteriaExpression lenderIndex = Builder.in(lenderField, ImmutableList.of(onePartyName.toString(), anotherPartyName.toString()));
        QueryCriteria lenderCriteria = new QueryCriteria.VaultCustomQueryCriteria(lenderIndex);


        Field borrowerField = IOUSchemaV2.PersistentIOU.class.getDeclaredField("borrower");
        CriteriaExpression borrowerIndex = Builder.in(borrowerField, ImmutableList.of(onePartyName.toString(),anotherPartyName.toString()));
        QueryCriteria borrowerCriteria = new QueryCriteria.VaultCustomQueryCriteria(borrowerIndex);

        Field dateField = IOUSchemaV2.PersistentIOU.class.getDeclaredField("date");
        CriteriaExpression dateIndex = Builder.between(dateField, from, to);
        QueryCriteria dateCriteria = new QueryCriteria.VaultCustomQueryCriteria(dateIndex);

        QueryCriteria criteria = generalCriteria.and(lenderCriteria)
                .and(borrowerCriteria)
                .and(dateCriteria);

        List<StateAndRef<IOUState>> results = rpcOps.vaultQueryByCriteria(criteria,IOUState.class).getStates();
        return results;

    }


    @GET
    @Path("ious-parties-dates")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIOUsByPartiesAndDates( @QueryParam("onePartyName") CordaX500Name onePartyName,
                                              @QueryParam("anotherPartyName") CordaX500Name anotherPartyName,
                                              @QueryParam("from") Date from,
                                              @QueryParam("to") Date to) throws NoSuchFieldException {
        if (from == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'from' missing or has wrong format.\n");
        }

        if (to == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'to' missing or has wrong format.\n");
        }

        if (from.getTime() > to.getTime()) {
            return ResponseStatus(BAD_REQUEST, "Invalid period, 'from' parameter is greater than 'to'.\n");
        }

        final Party oneParty = rpcOps.wellKnownPartyFromX500Name(onePartyName);
        if (oneParty == null) {
            return ResponseStatus(BAD_REQUEST, "Party named " + onePartyName + "cannot be found.\n");
        }

        final Party anotherParty = rpcOps.wellKnownPartyFromX500Name(anotherPartyName);
        if (anotherParty == null) {
            return ResponseStatus(BAD_REQUEST, "Party named " + anotherPartyName + "cannot be found.\n");
        }

        try {
            List<StateAndRef<IOUState>> results = getIOUsByPartiesAndDates_(onePartyName,anotherPartyName,from,to);
            return Response.status(OK).entity(results).build();
        } catch (Exception ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return ResponseStatus(BAD_REQUEST, msg);
        }
    }



    @PUT
    @Path("compensate")
    public Response createIPU(@QueryParam("viewerPartyName") CordaX500Name viewerPartyName,
                              @QueryParam("counterPartyName") CordaX500Name counterPartyName,
                              @QueryParam("from") Date from,
                              @QueryParam("to") Date to) throws InterruptedException, ExecutionException {


        Party me = rpcOps.nodeInfo().getLegalIdentities().get(0);

        if (from == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'from' missing or has wrong format.\n");
        }

        if (to == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'to' missing or has wrong format.\n");
        }

        if (from.getTime() > to.getTime()) {
            return ResponseStatus(BAD_REQUEST, "Invalid period, 'from' parameter is greater than 'to'.\n");
        }

        if (viewerPartyName == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'viewerPartyName' missing or has wrong format.\n");
        }

        if (counterPartyName == null) {
            return ResponseStatus(BAD_REQUEST, "Query parameter 'counterparty' missing or has wrong format.\n");
        }

        final Party viewerParty = rpcOps.wellKnownPartyFromX500Name(viewerPartyName);
        if (viewerParty == null) {
            return ResponseStatus(BAD_REQUEST, "Party named " + viewerPartyName + "cannot be found.\n");
        }

        final Party counterPartyParty = rpcOps.wellKnownPartyFromX500Name(counterPartyName);
        if (counterPartyParty == null) {
            return ResponseStatus(BAD_REQUEST, "Party named " + counterPartyName + "cannot be found.\n");
        }


        try {
            final List<StateAndRef<IOUState>> inputs = this.getIOUsByPartiesAndDates_(counterPartyName, me.getName(), from, to);

            if (inputs == null || inputs.size() <= 0) {
                return ResponseStatus(BAD_REQUEST, "Nothing to compensate between " + counterPartyName + " and " + me +".\n");
            }
            final IPU ipu = XUtils.compensate(inputs);
            if (ipu == null ) {
                return ResponseStatus(BAD_REQUEST, "Something wrong happened. There are some monkeys that are working in it.\n");
            }
            final Party payer = ipu.payer;
            final Party loaner = ipu.loaner;
            final int ipuValue = ipu.value;
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CompensationFlow.Initiator.class, inputs, viewerParty, payer, loaner, ipuValue)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return ResponseStatus(CREATED, msg);

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return ResponseStatus(BAD_REQUEST, msg);
        }
    }
    
    public static Response ResponseStatus(Response.StatusType statusType, String msg) {
        //ImmutableMap.of("message", msg)
        return Response.status(statusType).entity(msg ).build();
    } 
}
