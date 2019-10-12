package net.corda.cdmsupport.validators

import com.regnosys.rosetta.common.validation.RosettaTypeValidator
import com.rosetta.model.lib.path.RosettaPath
import com.rosetta.model.lib.validation.ValidationResult
import com.rosetta.model.lib.validation.Validator
import net.corda.cdmsupport.ExecutionAlreadyExists
import org.isda.cdm.*
import org.isda.cdm.meta.*

class CdmValidators() {

    fun validateEvent(event: Event): List<ValidationResult<in Event>> {
        val eventMeta = EventMeta()
        val validators = ArrayList<Validator<in Event>>()
        validators.addAll(eventMeta.choiceRuleValidators())
        validators.addAll(eventMeta.dataRules())
        validators.add(eventMeta.validator())

        return validators.map { it.validate(RosettaPath.valueOf("Event"), event) }.toList()
    }

    fun validateExecution(execution: Execution): List<ValidationResult<in Execution>>{
        //TODO Your code here
        val executionMeta = ExecutionMeta();
        val validators = ArrayList<Validator<in Execution>>()
        validators.addAll(executionMeta.choiceRuleValidators())

        return validators.map { it.validate(RosettaPath.valueOf("Execution"), execution) }.toList()
        //System.out.println("Execution:" + execution);
    }

    fun validateExecutionPrimitive(executionPrimitive: ExecutionPrimitive) {
        //TODO Your code here
        //System.out.println("ExecutionPrimitive:" + executionPrimitive);
    }

    fun validateAllocationPrimitive(allocationPrimitive: AllocationPrimitive) {
        //TODO Your code here
        //System.out.println("AllocationPrimitive:" + allocationPrimitive);
    }

    fun validateAffirmation(affirmation: Affirmation) {
        //TODO Your code here
        //System.out.println("Affirmation:" + affirmation);
    }

    fun validateConfirmation(confirmation: Confirmation) {
        //TODO Your code here
        System.out.println("Confirmation:" + confirmation);
    }

}
