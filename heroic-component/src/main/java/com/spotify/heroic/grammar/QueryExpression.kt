/*
 * Copyright (c) 2019 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"): you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.heroic.grammar

import com.fasterxml.jackson.annotation.JsonProperty
import com.spotify.heroic.filter.Filter
import com.spotify.heroic.metric.MetricType
import java.util.*

data class QueryExpression(
    @JvmField val context: Context,
    val select: Optional<Expression>,
    val source: Optional<MetricType>,
    val range: Optional<RangeExpression>,
    val filter: Optional<Filter>,
    val with: Map<String, Expression>,
    @JsonProperty("as") val asExpression: Map<String, Expression>
): Expression {
    override fun getContext() = context

    override fun eval(scope: Expression.Scope): Expression =
        QueryExpression(
            context,
            select.map { it.eval(scope) },
            source,
            range.map { it.eval(scope) },
            filter,
            Expression.evalMap(with, scope),
            Expression.evalMap(asExpression, scope)
        )

    override fun <R : Any?> visit(visitor: Expression.Visitor<R>): R {
        return visitor.visitQuery(this)
    }

    override fun toRepr() = """{select: $select source: $source, 
        range: $range, filter: $filter, with: $with, as: $asExpression}""".trimIndent()
}
