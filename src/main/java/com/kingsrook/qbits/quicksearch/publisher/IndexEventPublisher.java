/*
 * Copyright 2024 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
