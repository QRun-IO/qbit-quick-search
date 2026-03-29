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

package com.kingsrook.qbits.quicksearch.publisher;


import java.util.List;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Strategy interface for publishing index events to the search engine.
 **
 ** Implementations determine how events are dispatched (synchronously,
 ** asynchronously via a queue, etc.).
 *******************************************************************************/
public interface IndexEventPublisher
{

   /*******************************************************************************
    ** Publish a batch of INDEX events, causing the corresponding records to be
    ** added or updated in the search index.
    **
    ** @param events the index events to publish
    ** @throws QException if the publish operation fails
    *******************************************************************************/
   void publishIndexEvents(List<IndexEvent> events) throws QException;



   /*******************************************************************************
    ** Publish a batch of DELETE events, causing the corresponding records to be
    ** removed from the search index.
    **
    ** @param events the delete events to publish
    ** @throws QException if the publish operation fails
    *******************************************************************************/
   void publishDeleteEvents(List<IndexEvent> events) throws QException;



   /*******************************************************************************
    ** Release any resources held by this publisher.
    **
    ** @throws QException if the close operation fails
    *******************************************************************************/
   void close() throws QException;

}
