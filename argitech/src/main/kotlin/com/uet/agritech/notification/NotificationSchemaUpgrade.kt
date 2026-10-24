package com.uet.agritech.notification

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import java.sql.DatabaseMetaData
import java.sql.Types
import javax.sql.DataSource

/** Hibernate schema update does not relax NOT NULL on an existing MySQL column. */
@Component
@DependsOn("entityManagerFactory")
@ConditionalOnProperty(name = ["spring.jpa.hibernate.ddl-auto"], havingValue = "update")
class NotificationSchemaUpgrade(private val dataSource: DataSource) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        dataSource.connection.use { connection ->
            val metadata = connection.metaData
            if (metadata.databaseProductName != "MySQL") return
            metadata.getColumns(connection.catalog, null, "user_notifications", "order_id").use { columns ->
                if (!columns.next() || columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) return
                check(columns.getInt("DATA_TYPE") == Types.BIGINT) {
                    "Unexpected type for user_notifications.order_id; review the schema before upgrading."
                }
                val type = if (columns.getString("TYPE_NAME").contains("UNSIGNED", ignoreCase = true))
                    "BIGINT UNSIGNED" else "BIGINT"
                connection.createStatement().use { statement ->
                    statement.executeUpdate("ALTER TABLE user_notifications MODIFY COLUMN order_id $type NULL")
                }
                logger.info("Updated user_notifications.order_id to allow notifications without an order")
            }
        }
    }
}
