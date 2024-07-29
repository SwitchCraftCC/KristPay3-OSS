/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import io.sc3.kristpay.core.config.CONFIG
import java.sql.Connection

class ConnectionManager {

    private val datasource: HikariDataSource
    private val connection: Connection get() = datasource.connection
    val database by lazy { Database.connect( getNewConnection = { connection }, databaseConfig = DatabaseConfig.invoke {
        sqlLogger = object : SqlLogger, KLogging() {
            override fun log(context: StatementContext, transaction: Transaction) {
                logger.trace("SQL: " + context.sql(transaction) + " with args " + context.args.joinToString())
            }
        }
    } ) }

    init {
        val config = HikariConfig().apply {
            with(CONFIG) {
                jdbcUrl = jdbc.url
                username = jdbc.user
                password = jdbc.password
            }
        }

        datasource = HikariDataSource(config)
    }

}
