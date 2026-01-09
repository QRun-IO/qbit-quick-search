/*
 * Copyright 2024 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
