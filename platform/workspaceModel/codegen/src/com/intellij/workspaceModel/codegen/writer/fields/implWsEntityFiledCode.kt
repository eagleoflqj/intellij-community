package org.jetbrains.deft.codegen.ijws.fields

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.impl.*
import deft.storage.codegen.*
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.codegen.ijws.getRefType
import org.jetbrains.deft.codegen.ijws.isRefType
import org.jetbrains.deft.codegen.ijws.refsFields
import org.jetbrains.deft.codegen.model.KtObjModule
import org.jetbrains.deft.codegen.utils.fqn1
import org.jetbrains.deft.codegen.utils.fqn2
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.impl.fields.MemberOrExtField

val Field<*, *>.implWsEntityFieldCode: String
  get() = buildString {
    if (hasSetter) {
      if (isOverride && name !in listOf("name", "entitySource")) {
        append(implWsBlockingCodeOverride)
      }
      else append(implWsBlockingCode)
    } else {
      append("override var $javaName: ${type.javaType} = super<${owner.javaFullName}>.$javaName\n")
    }
  }

private val Field<*, *>.implWsBlockingCode: String
  get() = implWsBlockCode(type, name, "")

internal fun Field<*, *>.implWsBlockCode(fieldType: ValueType<*>, name: String, optionalSuffix: String = ""): String {
  return when (fieldType) {
    TInt -> "override var $javaName: ${fieldType.javaType} = 0"
    TBoolean -> "override var $javaName: ${fieldType.javaType} = false"
    TString -> """            
            @JvmField var $implFieldName: String? = null
            override val $javaName: ${fieldType.javaType}${optionalSuffix}
                get() = $implFieldName${if (optionalSuffix.isBlank()) "!!" else ""}
                                
        """.trimIndent()
    is TRef -> {
      val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
      """
            override val $name: ${fieldType.javaType}$optionalSuffix
                get() = snapshot.${refsConnectionMethodCode()}$notNullAssertion           
                
            """.trimIndent()
    }
    is TList<*> -> {
      if (fieldType.isRefType()) {
        val connectionName = name.uppercase() + "_CONNECTION_ID"
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
        if ((fieldType.elementType as TRef<*>).targetObjType.abstract) {
          """
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = snapshot.${fqn2(EntityStorage::extractOneToAbstractManyChildren)}<${fieldType.elementType.javaType}>($connectionName, this)$notNullAssertion.toList()
               
                """.trimIndent()
        }
        else {
          """
                override val $name: ${fieldType.javaType}$optionalSuffix
                    get() = snapshot.${fqn2(EntityStorage::extractOneToManyChildren)}<${fieldType.elementType.javaType}>($connectionName, this)$notNullAssertion.toList()
               
                """.trimIndent()
        }
      }
      else {
        val notNullAssertion = if (optionalSuffix.isBlank()) "!!" else ""
        """
                @JvmField var $implFieldName: ${fieldType.javaType}? = null
                override val $javaName: ${fieldType.javaType}$optionalSuffix
                    get() = $implFieldName$notNullAssertion   
                
                """.trimIndent()
      }
    }
    is TMap<*, *> -> """
            @JvmField var $implFieldName: ${fieldType.javaType}? = null
            override val $javaName: ${fieldType.javaType}$optionalSuffix
                get() = $implFieldName${if (optionalSuffix.isBlank()) "!!" else ""}
        """.trimIndent()
    is TOptional<*> -> when (fieldType.type) {
      TInt, TBoolean -> "override var $javaName: ${fieldType.javaType} = null"
      else -> implWsBlockCode(fieldType.type, name, "?")
    }
    is TBlob<*> -> """            
            @JvmField var $implFieldName: ${fieldType.javaSimpleName}? = null
            override val $javaName: ${fieldType.javaSimpleName}$optionalSuffix
                get() = $implFieldName${if (optionalSuffix.isBlank()) "!!" else ""}
                                
        """.trimIndent()
    else -> error("Unsupported field type: $this")
  }
}

internal val Field<*, *>.implWsBlockingCodeOverride: String
  get() {
    val originalField = owner.structure.refsFields.first { it.type.javaType == type.javaType }
    val connectionName = originalField.name.uppercase() + "_CONNECTION_ID"
    var valueType = referencedField.type
    val notNullAssertion = if (valueType is TOptional<*>) "" else "!!"
    if (valueType is TOptional<*>) {
      valueType = valueType.type as ValueType<Any?>
    }
    val getterName = when (valueType) {
      is TList<*> -> if (owner.abstract)
        fqn1(EntityStorage::extractOneToAbstractManyParent)
      else
        fqn1(EntityStorage::extractOneToManyParent)
      is TRef<*> -> if (owner.abstract)
        fqn1(EntityStorage::extractOneToAbstractOneParent)
      else
        fqn1(EntityStorage::extractOneToOneParent)
      else -> error("Unsupported reference type")
    }
    return """
            override val $javaName: ${type.javaType}
                get() = snapshot.$getterName($connectionName, this)$notNullAssertion
                
        """.trimIndent()
  }

internal val MemberOrExtField<*, *>.referencedField: MemberOrExtField<*, *>
  get() {
    val ref = type.getRefType()
    val declaredReferenceFromChild =
      ref.targetObjType.structure.refsFields.filter { it.type.getRefType().targetObjType == owner && it != this } +
      ((ref.targetObjType.module as? KtObjModule)?.extFields?.filter { it.type.getRefType().targetObjType == owner && it.owner == ref.targetObjType && it != this }
       ?: emptyList())
    if (declaredReferenceFromChild.isEmpty()) {
      error("Reference should be declared at both entities. It exist at ${owner.name}#$name but absent at ${ref.targetObjType.name}")
    }
    if (declaredReferenceFromChild.size > 1) {
      error(
        "More then one reference to ${owner.name} declared at ${declaredReferenceFromChild[0].owner}#${declaredReferenceFromChild[0].name}," +
        "${declaredReferenceFromChild[1].owner}#${declaredReferenceFromChild[1].name}")
    }
    return declaredReferenceFromChild[0]
  }
