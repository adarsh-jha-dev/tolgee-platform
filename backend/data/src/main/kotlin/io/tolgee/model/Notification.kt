/**
 * Copyright (C) 2023 Tolgee s.r.o. and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tolgee.model

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import io.tolgee.model.activity.ActivityModifiedEntity
import io.tolgee.model.activity.ActivityRevision
import io.tolgee.model.batch.BatchJob
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import org.hibernate.annotations.UpdateTimestamp
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.ManyToOne
import javax.persistence.OrderBy
import javax.persistence.SequenceGenerator
import javax.persistence.Temporal
import javax.persistence.TemporalType

@Entity
@TypeDefs(
  value = [
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
  ]
)
class Notification(
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: NotificationType,

  // This data is very likely to be useless: lazy
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(nullable = false)
  val recipient: UserAccount,

  // We most definitely need this to show the notification: eager
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(nullable = false)
  val project: Project,

  // We most definitely need this to show the notification: eager
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "notification_activity_revisions")
  val activityRevisions: MutableSet<ActivityRevision> = mutableSetOf(),

  // We most definitely need this to show the notification: eager
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "notification_activity_modified_entities")
  val activityModifiedEntities: MutableSet<ActivityModifiedEntity> = mutableSetOf(),

  // We most definitely need this to show the notification: eager
  @ManyToOne(fetch = FetchType.EAGER)
  val batchJob: BatchJob? = null,

  @Type(type = "jsonb")
  val meta: MutableMap<String, Any?>
) {
  @Id
  @SequenceGenerator(name = "notification_seq", sequenceName = "sequence_notifications")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_seq")
  val id: Long = 0

  @Column(nullable = false)
  @ColumnDefault("true")
  var unread: Boolean = true

  @Temporal(TemporalType.TIMESTAMP)
  var markedDoneAt: Date? = null

  @OrderBy
  @UpdateTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  val lastUpdated: Date = Date()

  enum class NotificationType {
    ACTIVITY,
    BATCH_JOB_FAILURE,
  }
}
