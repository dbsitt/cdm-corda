package net.corda.cdmsupport.events

import com.derivhack.CollateralFlow
import com.derivhack.CollateralTopupFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test

class CollateralTest : BaseEventTestGJ() {

    @Test
    fun testCollateral() {
        node6.services.startFlow(CollateralTopupFlow("Client1")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
        node6.services.startFlow(CollateralTopupFlow("Client2")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
        node6.services.startFlow(CollateralTopupFlow("Client3")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
        node6.services.startFlow(CollateralTopupFlow("Broker1")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)


        var collateralTx = node6.services.startFlow(CollateralFlow()).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
        checkTheBasicFabricOfTheTransaction(collateralTx, 4, 4, 0, 4)

        println("--------------------------------------Second transaction---------------------------")
        collateralTx = node6.services.startFlow(CollateralFlow()).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
        checkTheBasicFabricOfTheTransaction(collateralTx, 2, 4, 0, 4)
    }

}