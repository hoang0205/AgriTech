package com.uet.agritech.notification

import io.mockk.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import javax.sql.DataSource

class NotificationSchemaUpgradeTest {
    private val source = mockk<DataSource>()
    private val connection = mockk<Connection>(relaxUnitFun = true)
    private val metadata = mockk<DatabaseMetaData>()
    private val columns = mockk<ResultSet>(relaxUnitFun = true)
    private val statement = mockk<Statement>(relaxUnitFun = true)

    private fun setup(nullable: Boolean, type: String = "BIGINT") {
        every { source.connection } returns connection
        every { connection.metaData } returns metadata
        every { connection.catalog } returns "agritech"
        every { metadata.databaseProductName } returns "MySQL"
        every { metadata.getColumns("agritech", null, "user_notifications", "order_id") } returns columns
        every { columns.next() } returns true
        every { columns.getInt("NULLABLE") } returns if (nullable) DatabaseMetaData.columnNullable else DatabaseMetaData.columnNoNulls
        every { columns.getInt("DATA_TYPE") } returns Types.BIGINT
        every { columns.getString("TYPE_NAME") } returns type
        every { connection.createStatement() } returns statement
        every { statement.executeUpdate(any()) } returns 0
    }

    @Test fun `legacy required order id is relaxed without replacing notification table`() {
        setup(false)
        NotificationSchemaUpgrade(source).run(mockk())
        verify(exactly = 1) { statement.executeUpdate("ALTER TABLE user_notifications MODIFY COLUMN order_id BIGINT NULL") }
    }
    @Test fun `already nullable database does not execute ddl`() {
        setup(true)
        NotificationSchemaUpgrade(source).run(mockk())
        verify(exactly = 0) { connection.createStatement() }
    }
    @Test fun `unsigned legacy column keeps unsigned type`() {
        setup(false, "BIGINT UNSIGNED")
        NotificationSchemaUpgrade(source).run(mockk())
        verify { statement.executeUpdate("ALTER TABLE user_notifications MODIFY COLUMN order_id BIGINT UNSIGNED NULL") }
    }
}
