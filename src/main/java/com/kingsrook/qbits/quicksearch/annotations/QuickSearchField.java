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
 ** Marks a field as searchable within a Quick Search indexed entity.
 *******************************************************************************/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface QuickSearchField
{

   /***************************************************************************
    ** Weight/boost for this field in search ranking. Higher = more important.
    ***************************************************************************/
   int weight() default 1;


   /***************************************************************************
    ** Whether to include the field label as a prefix in indexed text.
    ***************************************************************************/
   boolean includeLabel() default false;

}
