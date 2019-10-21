package net.corda.cdmsupport

import org.isda.cdm.Party
import org.isda.cdm.Quantity
import org.isda.cdm.Security

data class CollateralInstructionWrapper(val collateralInstructions: CollateralInstructions)

data class CollateralInstructions(val eventDate: String, val client: Party, val clientSegregated: Party, val security: Security, val netIM: NetIM)

data class NetIM(val quantity: Quantity)

data class ExecutionRequest(val client: String, val executingEntity: String, val counterParty: String, val product: String, val quantity: Long, val price: Double, val tradeDate: String, val eventDate: String, val buySell: String)

data class AllocationRequest(val executionRef: String, val amount1: Long, val amount2: Long)