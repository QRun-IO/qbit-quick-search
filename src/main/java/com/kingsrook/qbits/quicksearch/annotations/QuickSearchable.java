/*
 * QBit Quick Search
 * Copyright (C) 2024-2025 QRun-IO, LLC
 * https://www.qrun.io | https://github.com/QRun-IO
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.kingsrook.qbits.quicksearch.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/*******************************************************************************
 ** Marks an entity class as searchable via the Quick Search QBit.
 *******************************************************************************/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QuickSearchable
{

   /***************************************************************************
    ** The name of the QQQ table this entity represents.
    ***************************************************************************/
   String tableName();


   /***************************************************************************
    ** Fields to include in searchable text. If empty, scans for @QuickSearchField.
    ***************************************************************************/
   String[] fields() default {};


   /***************************************************************************
    ** Interval in minutes for basepull indexing. Default 5 minutes.
    ***************************************************************************/
   int basepullIntervalMinutes() default 5;


   /***************************************************************************
    ** Field name containing modification timestamp for basepull queries.
    ***************************************************************************/
   String basepullTimestampField() default "modifyDate";


   /***************************************************************************
    ** Whether this table is enabled for indexing by default.
    ***************************************************************************/
   boolean enabledByDefault() default true;

}
