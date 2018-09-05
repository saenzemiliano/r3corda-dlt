package com.example;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.driver.*;
import net.corda.testing.node.NotarySpec;
import net.corda.testing.node.User;

import java.util.List;

import static net.corda.testing.driver.Driver.driver;

/**
 * This file is exclusively for being able to run your nodes through an IDE.
 * Do not use in a production environment.
 */
public class NodeDriver {

    public static void main(String[] args) {
        final User user = new User("user1", "test", ImmutableSet.of("ALL"));

        driver(new DriverParameters().withWaitForAllNodesToFinish(true).withNotarySpecs(ImmutableList.of(
                    new NotarySpec(new CordaX500Name("Notary", "Uruguay", "UY"), true, ImmutableList.of(user), VerifierType.InMemory, null)
                )),
                dsl -> {
                    List<CordaFuture<NodeHandle>> nodeFutures = ImmutableList.of(
                            dsl.startNode(new NodeParameters()
                                    .withProvidedName(new CordaX500Name("Viewer", "Uruguay", "UY"))
                                    .withCustomOverrides(ImmutableMap.of("rpcSettings.address", "localhost:10016", "rpcSettings.adminAddress", "localhost:10058", "webAddress", "localhost:10017"))
                                    .withRpcUsers(ImmutableList.of(user))),
                            dsl.startNode(new NodeParameters()
                                    .withProvidedName(new CordaX500Name("PartyA", "London", "GB"))
                                    .withCustomOverrides(ImmutableMap.of("rpcSettings.address", "localhost:10008", "rpcSettings.adminAddress", "localhost:10048", "webAddress", "localhost:10009"))
                                    .withRpcUsers(ImmutableList.of(user))),
                            dsl.startNode(new NodeParameters()
                                    .withProvidedName(new CordaX500Name("PartyB", "New York", "US"))
                                    .withCustomOverrides(ImmutableMap.of("rpcSettings.address", "localhost:10011", "rpcSettings.adminAddress", "localhost:10051", "webAddress", "localhost:10012"))
                                    .withRpcUsers(ImmutableList.of(user)))/*,
                            dsl.startNode(new NodeParameters()
                                    .withProvidedName(new CordaX500Name("PartyC", "Paris", "FR"))
                                    .withCustomOverrides(ImmutableMap.of("rpcSettings.address", "localhost:10014", "rpcSettings.adminAddress", "localhost:10054", "webAddress", "localhost:10015"))
                                    .withRpcUsers(ImmutableList.of(user)))*/);

                    try {
                        for (CordaFuture<NodeHandle> cordaFuture  : nodeFutures) {
                            dsl.startWebserver(cordaFuture.get());
                        }

                    } catch (Throwable e) {
                        System.err.println("Encountered exception in node startup: " + e.getMessage());
                        e.printStackTrace();
                    }

                    return null;
                }
        );

    }

}
