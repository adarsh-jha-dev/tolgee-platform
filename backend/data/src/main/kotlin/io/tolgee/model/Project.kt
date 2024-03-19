package io.tolgee.model

import io.tolgee.activity.annotation.ActivityLoggedProp
import io.tolgee.api.ISimpleProject
import io.tolgee.exceptions.NotFoundException
import io.tolgee.model.automations.Automation
import io.tolgee.model.contentDelivery.ContentDeliveryConfig
import io.tolgee.model.contentDelivery.ContentStorage
import io.tolgee.model.key.Key
import io.tolgee.model.key.Namespace
import io.tolgee.model.mtServiceConfig.MtServiceConfig
import io.tolgee.model.webhook.WebhookConfig
import io.tolgee.service.LanguageService
import io.tolgee.service.project.ProjectService
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderBy
import jakarta.persistence.PostLoad
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.annotations.ColumnDefault
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import java.util.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["address_part"], name = "project_address_part_unique")])
@EntityListeners(Project.Companion.ProjectListener::class)
class Project(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  override var id: Long = 0L,
  @field:NotBlank
  @field:Size(min = 3, max = 50)
  @ActivityLoggedProp
  override var name: String = "",
  @field:Size(min = 3, max = 2000)
  @ActivityLoggedProp
  @Column(length = 2000)
  override var description: String? = null,
  @field:Size(max = 2000)
  @Column(columnDefinition = "text")
  @ActivityLoggedProp
  var aiTranslatorPromptDescription: String? = null,
  @Column(name = "address_part")
  @ActivityLoggedProp
  @field:Size(min = 3, max = 60)
  @field:Pattern(regexp = "^[a-z0-9-]*[a-z]+[a-z0-9-]*$", message = "invalid_pattern")
  override var slug: String? = null,
) : AuditModel(), ModelWithAvatar, EntityWithId, SoftDeletable, ISimpleProject {
  @OrderBy("id")
  @OneToMany(fetch = FetchType.LAZY, mappedBy = "project")
  var languages: MutableSet<Language> = LinkedHashSet()

  @OneToMany(mappedBy = "project")
  var permissions: MutableSet<Permission> = LinkedHashSet()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "project")
  var keys: MutableList<Key> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "project")
  var apiKeys: MutableSet<ApiKey> = LinkedHashSet()

  @ManyToOne(optional = true, fetch = FetchType.LAZY)
  @Deprecated(message = "Project can be owned only by organization")
  var userOwner: UserAccount? = null

  @ManyToOne(optional = true)
  lateinit var organizationOwner: Organization

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST])
  @ActivityLoggedProp
  var baseLanguage: Language? = null

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.REMOVE], mappedBy = "project", orphanRemoval = true)
  var autoTranslationConfigs: MutableList<AutoTranslationConfig> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "project")
  var mtServiceConfig: MutableList<MtServiceConfig> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "project")
  var namespaces: MutableList<Namespace> = mutableListOf()

  @ActivityLoggedProp
  override var avatarHash: String? = null

  @Transient
  override var disableActivityLogging = false

  @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "project")
  var automations: MutableList<Automation> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "project")
  var contentDeliveryConfigs: MutableList<ContentDeliveryConfig> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "project")
  var contentStorages: MutableList<ContentStorage> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "project")
  var webhookConfigs: MutableList<WebhookConfig> = mutableListOf()

  @ColumnDefault("true")
  override var icuPlaceholders: Boolean = true

  override var deletedAt: Date? = null

  constructor(name: String, description: String? = null, slug: String?, organizationOwner: Organization) :
    this(id = 0L, name, description, slug) {
    this.organizationOwner = organizationOwner
  }

  fun findLanguageOptional(tag: String): Optional<Language> {
    return languages.stream().filter { l: Language -> (l.tag == tag) }.findFirst()
  }

  fun findLanguage(tag: String): Language? {
    return findLanguageOptional(tag).orElse(null)
  }

  fun getLanguage(tag: String): Language {
    return findLanguage(tag) ?: throw NotFoundException()
  }

  companion object {
    @Configurable
    class ProjectListener {
      @Autowired
      lateinit var languageService: ObjectFactory<LanguageService>

      @Autowired
      lateinit var projectService: ObjectFactory<ProjectService>

      @PrePersist
      @PreUpdate
      fun preSave(project: Project) {
        if (!(!project::organizationOwner.isInitialized).xor(project.userOwner == null)) {
          throw Exception("Exactly one of organizationOwner or userOwner must be set!")
        }
      }

      @PostLoad
      fun postLoad(project: Project) {
        if (project.baseLanguage == null) {
          languageService.`object`.setNewProjectBaseLanguage(project)
        }
      }
    }
  }
}
