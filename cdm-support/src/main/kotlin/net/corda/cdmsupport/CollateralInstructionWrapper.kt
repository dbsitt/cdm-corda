package net.corda.cdmsupport

import org.isda.cdm.Party
import org.isda.cdm.Quantity
import org.isda.cdm.Security

data class CollateralInstructionWrapper(val collateralInstructions: CollateralInstructions)

data class CollateralInstructions(val eventDate: String, val client: Party, val clientSegregated: Party, val security: Security, val netIM: NetIM)

data class NetIM(val quantity: Quantity)