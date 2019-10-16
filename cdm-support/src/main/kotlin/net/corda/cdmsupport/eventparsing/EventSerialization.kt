package net.corda.cdmsupport.eventparsing

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import java.io.File

fun serializeCdmObjectIntoJson(cdmObject: Any): String {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    return rosettaObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cdmObject)
}


fun serializeCdmObjectIntoFile(cdmObject: Any, fileName: String) {
    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    val content = rosettaObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cdmObject)
    File(fileName).writeText(content)
}

