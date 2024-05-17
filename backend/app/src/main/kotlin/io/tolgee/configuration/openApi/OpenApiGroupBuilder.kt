package io.tolgee.configuration.openApi

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.tolgee.configuration.openApi.activity.ModificationsSchemaGenerator
import io.tolgee.model.key.Key
import io.tolgee.openApiDocs.OpenApiCloudExtension
import io.tolgee.openApiDocs.OpenApiEeExtension
import io.tolgee.openApiDocs.OpenApiOrderExtension
import io.tolgee.openApiDocs.OpenApiSelfHostedExtension
import io.tolgee.security.authentication.AllowApiAccess
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Method

typealias OperationHandlers = HashMap<String, HandlerMethod>
typealias HandlerPaths = HashMap<Method, MutableList<String>>

class OpenApiGroupBuilder(
  private val groupName: String,
  private val fn: OpenApiGroupBuilder.() -> Unit,
) {
  val builder: GroupedOpenApi.Builder by lazy { GroupedOpenApi.builder().group(groupName) }
  val operationHandlers = OperationHandlers()
  val handlerPaths = HandlerPaths()

  val result: GroupedOpenApi by lazy {
    extractHandlerPaths()
    fn(this)
    setResponseContentToJson()

    // this customizer has to be applied last, because that way it's executed first 🤷‍
    extractOperationHandlers()

    handleLinks()

    OpenApiSecurityHelper(this).handleSecurity()

    addExtensions()

    cleanUnusedModels()

    addSchmeForTesting()

    return@lazy builder.build()
  }

  // todo: Remove this!
  private fun addSchmeForTesting() {
    val keySchema = ModificationsSchemaGenerator().getModificationSchema(Key::class)
    builder.addOpenApiCustomizer {
      it.schema("KeyModifiedEntity", keySchema)
    }
  }

  private fun cleanUnusedModels() {
    builder.addOpenApiCustomizer {
      OpenApiUnusedSchemaCleaner(it).clean()
    }
  }

  private fun addExtensions() {
    addTagOrders()
    addMethodExtensions()
  }

  private fun addMethodExtensions() {
    customizeOperations { operation, handlerMethod, _ ->
      addOperationOrder(handlerMethod, operation)
      addOperationEeExtension(handlerMethod, operation)
      addSelfHostedOperationEeExtension(handlerMethod, operation)
      addCloudOperationEeExtension(handlerMethod, operation)
      operation
    }
  }

  private fun addOperationOrder(
    handlerMethod: HandlerMethod,
    operation: Operation,
  ) {
    val orderAnnotation = handlerMethod.getMethodAnnotation(OpenApiOrderExtension::class.java)
    if (orderAnnotation != null) {
      operation.addExtension("x-order", orderAnnotation.order)
    }
  }

  private fun addOperationEeExtension(
    handlerMethod: HandlerMethod,
    operation: Operation,
  ) {
    val eeExtensionAnnotation =
      handlerMethod.getMethodAnnotation(OpenApiEeExtension::class.java)
        ?: handlerMethod.method.declaringClass.getAnnotation(OpenApiEeExtension::class.java) ?: null
    if (eeExtensionAnnotation != null) {
      operation.addExtension("x-ee", true)
    }
  }

  private fun addCloudOperationEeExtension(
    handlerMethod: HandlerMethod,
    operation: Operation,
  ) {
    val eeExtensionAnnotation =
      handlerMethod.getMethodAnnotation(OpenApiCloudExtension::class.java)
        ?: handlerMethod.method.declaringClass.getAnnotation(OpenApiCloudExtension::class.java) ?: null
    if (eeExtensionAnnotation != null) {
      operation.addExtension("x-cloud", true)
    }
  }

  private fun addSelfHostedOperationEeExtension(
    handlerMethod: HandlerMethod,
    operation: Operation,
  ) {
    val eeExtensionAnnotation =
      handlerMethod.getMethodAnnotation(OpenApiSelfHostedExtension::class.java)
        ?: handlerMethod.method.declaringClass.getAnnotation(OpenApiSelfHostedExtension::class.java) ?: null
    if (eeExtensionAnnotation != null) {
      operation.addExtension("x-self-hosted", true)
    }
  }

  private fun addTagOrders() {
    val classTags = mutableMapOf<Class<*>, MutableSet<String>>()
    customizeOperations { operation, handlerMethod, path ->
      classTags.computeIfAbsent(handlerMethod.method.declaringClass) { mutableSetOf() }.apply {
        addAll(operation.tags)
      }
      operation
    }

    builder.addOpenApiCustomizer { openApi ->
      val declaringClasses =
        operationHandlers.mapNotNull {
          it.value.method.declaringClass
        }.toSet()

      val tagOrders =
        declaringClasses.flatMap { clazz ->
          val orderAnnotation = clazz.getAnnotation(OpenApiOrderExtension::class.java) ?: return@flatMap listOf()
          classTags[clazz]?.map { it to orderAnnotation.order } ?: listOf()
        }.groupBy { it.first }.map {
          val orders = it.value.map { it.second }.toSet()
          if (orders.size > 1) {
            throw RuntimeException("Multiple orders for tag ${it.key}: $orders")
          }
          it.key to orders.first()
        }.toMap()

      val tagsMap = openApi.tags.associateBy { it.name }.toMutableMap()
      tagOrders.forEach { (tagName, order) ->
        val tag =
          tagsMap.computeIfAbsent(tagName) {
            val tag = io.swagger.v3.oas.models.tags.Tag().name(tagName)
            openApi.tags.add(tag)
            tag
          }
        tag.addExtension("x-order", order)
      }

      openApi.tags.forEach {
        val order = tagOrders[it.name] ?: return@forEach
        it.addExtension("x-order", order)
      }
    }
  }

  private fun extractOperationHandlers() {
    builder.addOperationCustomizer { operation: Operation, handlerMethod: HandlerMethod ->
      operationHandlers[operation.operationId] = handlerMethod
      operation
    }
  }

  /**
   * If null is returned from callback function, operation is removed from the API.
   */
  fun customizeOperations(fn: (Operation, HandlerMethod, path: String) -> Operation?) {
    builder.addOpenApiCustomizer { openApi ->
      val newPaths = Paths()
      openApi.paths.forEach { pathEntry ->
        val operations = ArrayList<Operation>()
        val newPathItem = PathItem()
        val oldPathItem = pathEntry.value
        oldPathItem.readOperations().forEach { operation ->
          val newOperation =
            fn(
              operation,
              operationHandlers[operation.operationId] ?: throw RuntimeException("Operation handler not found"),
              pathEntry.key,
            )
          if (newOperation != null) {
            operations.add(newOperation)
          }
        }

        operations.forEach { operation ->
          newPathItem.operation(oldPathItem.getHttpMethod(operation), operation)
        }

        if (operations.isNotEmpty()) {
          newPaths.addPathItem(pathEntry.key, newPathItem)
        }
      }

      val usedTags = newPaths.flatMap { it.value.readOperations() }.flatMap { it.tags }
      openApi?.tags?.removeIf { !usedTags.contains(it.name) }

      openApi.paths = newPaths
    }
  }

  private fun setResponseContentToJson() {
    customizeOperations { operation, _, _ ->
      val successResponse = operation.responses?.get("200")
      val content = successResponse?.content
      val anyContent = content?.get("*/*")
      if (anyContent != null) {
        content["application/json"] = anyContent
        content.remove("*/*")
      }
      operation
    }
  }

  private fun extractHandlerPaths() {
    builder.addOpenApiCustomizer { openApi ->
      openApi.paths.forEach { (path, value) ->
        value.readOperations().forEach { operation ->
          operationHandlers[operation.operationId]?.method?.let { method ->
            handlerPaths[method] =
              handlerPaths[method].let {
                it?.run {
                  add(path)
                  this
                } ?: mutableListOf(path)
              }
          }
        }
      }
    }
  }

  private fun handleLinks() {
    builder.addOpenApiCustomizer {
      it.components?.schemas?.values?.forEach {
        it?.properties?.remove("_links")
      }
    }
  }

  fun isApiAccessAllowed(handlerMethod: HandlerMethod): Boolean {
    return handlerMethod.getMethodAnnotation(AllowApiAccess::class.java) != null
  }

  fun containsProjectIdParam(path: String): Boolean {
    return path.contains("{${PROJECT_ID_PARAMETER}}")
  }

  fun isProjectPath(path: String): Boolean {
    return path.matches(PROJECTS_PATH_REGEX)
  }

  private fun PathItem.getHttpMethod(operation: Operation): PathItem.HttpMethod? {
    return when (operation) {
      this.get -> PathItem.HttpMethod.GET
      this.delete -> PathItem.HttpMethod.DELETE
      this.head -> PathItem.HttpMethod.HEAD
      this.post -> PathItem.HttpMethod.POST
      this.patch -> PathItem.HttpMethod.PATCH
      this.put -> PathItem.HttpMethod.PUT
      this.options -> PathItem.HttpMethod.OPTIONS
      this.trace -> PathItem.HttpMethod.TRACE
      else -> null
    }
  }

  companion object {
    const val PROJECT_ID_PARAMETER = "projectId"
    private val PROJECTS_PATH_REGEX = "^/(?:api|v2)/projects?/.*".toRegex()
    val PUBLIC_ENDPOINT_REGEX = "^/(?:api|v2)/public/.*".toRegex()
  }
}
