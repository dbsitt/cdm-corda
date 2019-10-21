package net.corda.cdmsupport.eventparsing

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.AllocationRequest
import net.corda.cdmsupport.CollateralInstructionWrapper
import net.corda.cdmsupport.ExecutionRequest
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import org.isda.cdm.*

fun readEventFromJson(pathToResource: String): Event {
    val json = readTextFromFile(pathToResource)
    return parseEventFromJson(json)
}

fun parseEventFromJson(json: String): Event {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return rosettaObjectMapper.readValue<Event>(json, Event::class.java)
}


fun parseMoneyFromJson(json: String): Money {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return rosettaObjectMapper.readValue<Money>(json, Money::class.java)
}

fun parsePartyFromJson(json: String): Party {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return rosettaObjectMapper.readValue<Party>(json, Party::class.java)
}

fun parseCorllateralInstructionWrapperFromJson(json:String) : CollateralInstructionWrapper {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return  rosettaObjectMapper.readValue<CollateralInstructionWrapper>(json, CollateralInstructionWrapper::class.java)
}

fun parseExecutionRequestFromJson(json:String) : ExecutionRequest {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return  rosettaObjectMapper.readValue<ExecutionRequest>(json, ExecutionRequest::class.java)
}

fun parseAllocationRequestFromJson(json:String) : AllocationRequest {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return  rosettaObjectMapper.readValue<AllocationRequest>(json, AllocationRequest::class.java)
}

fun readTextFromFile(pathToResource: String): String {
    return CdmTransactionBuilder::class.java.getResource(pathToResource).readText()
}

