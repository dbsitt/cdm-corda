package net.corda.cdmsupport.validators

import com.rosetta.model.lib.path.RosettaPath
import com.rosetta.model.lib.validation.Validator
import org.isda.cdm.*
import org.isda.cdm.meta.*

class CdmValidators() {

    fun validateEvent(event: Event) {
        println("Validating Event")
        val eventMeta = EventMeta()
        val validators = ArrayList<Validator<in Event>>()
        validators.addAll(eventMeta.choiceRuleValidators())
        validators.addAll(eventMeta.dataRules())
        validators.add(eventMeta.validator())
        validators.map { it.validate(RosettaPath.valueOf("Event"), event) }
                //                .sorted(Comparator.comparing(ValidationResult::isSuccess, Boolean::compareTo)) // failures first

                .forEach(System.out::println)
    }

    fun validateExecution(execution: Execution){
        println("Validating Execution")
        val executionMeta = ExecutionMeta()
        val validators = ArrayList<Validator<in Execution>>()
        validators.addAll(executionMeta.choiceRuleValidators())
        validators.addAll(executionMeta.dataRules())
        validators.add(executionMeta.validator())
        validators.map { it.validate(RosettaPath.valueOf("Execution"), execution) }.forEach(System.out::println)
    }

    fun validateExecutionPrimitive(executionPrimitive: ExecutionPrimitive) {
        println("Validating Execution Primitive")
        val executionPrimitiveMeta = ExecutionPrimitiveMeta()
        val validators = ArrayList<Validator<in ExecutionPrimitive>>()
        validators.addAll(executionPrimitiveMeta.choiceRuleValidators())
        validators.addAll(executionPrimitiveMeta.dataRules())
        validators.add(executionPrimitiveMeta.validator())
        validators.map { it.validate(RosettaPath.valueOf("ExecutionPrimitive"), executionPrimitive) }.forEach(System.out::println)
    }

    fun validateAllocationPrimitive(allocationPrimitive: AllocationPrimitive) {
        println("Validating Allocation Primitive")
        val allocationPrimitiveMeta = AllocationPrimitiveMeta()
        val validators = ArrayList<Validator<in AllocationPrimitive>>()
        validators.addAll(allocationPrimitiveMeta.choiceRuleValidators())
        validators.addAll(allocationPrimitiveMeta.dataRules())
        validators.add(allocationPrimitiveMeta.validator())
        validators.map { it.validate(RosettaPath.valueOf("AllocationPrimitive"), allocationPrimitive) }.forEach(System.out::println)
    }

    fun validateAffirmation(affirmation: Affirmation) {
        println("Validating Affirmation Primitive")
        val affirmationMeta = AffirmationMeta()
        val validators = ArrayList<Validator<in Affirmation>>()
        validators.addAll(affirmationMeta.choiceRuleValidators())
        validators.addAll(affirmationMeta.dataRules())
        validators.add(affirmationMeta.validator())
        validators.map { it.validate(RosettaPath.valueOf("Affirmation"), affirmation) }.forEach(System.out::println)
    }

    fun validateConfirmation(affirmation: Confirmation) {
        println("Validating Confirmation")
        val confirmationMeta = ConfirmationMeta()
        val validators = ArrayList<Validator<in Confirmation>>()
        validators.addAll(confirmationMeta.choiceRuleValidators())
        validators.addAll(confirmationMeta.dataRules())
        validators.add(confirmationMeta.validator())
        validators.map { it.validate(RosettaPath.valueOf("Confirmation"), affirmation) }.forEach(System.out::println)
    }

}
