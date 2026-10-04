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

package com.kingsrook.qbits.quicksearch.opensearch;


import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;


/*******************************************************************************
 ** Escape hatch for hosts that need something the built-in auth modes do not
 ** cover: bearer or JWT tokens, API-key headers, request interceptors, proxy
 ** settings, or client certificates loaded from a custom source.
 **
 ** Register an implementation on QuickSearchQBitConfig.transportCustomizer as a
 ** QCodeReference. Both hooks run after the QBit has applied its own settings
 ** (auth, TLS, timeouts) and before the transport is built. Not used for the
 ** AWS_SIGV4 transport, which is built by the AWS SDK.
 *******************************************************************************/
public interface OpenSearchTransportCustomizer
{

   /*******************************************************************************
    ** Adjust the transport builder (default headers, request config callback).
    *******************************************************************************/
   default ApacheHttpClient5TransportBuilder customizeTransport(ApacheHttpClient5TransportBuilder builder)
   {
      return (builder);
   }



   /*******************************************************************************
    ** Adjust the Apache HttpAsyncClient builder (interceptors, connection
    ** manager, credentials).
    *******************************************************************************/
   default HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder builder)
   {
      return (builder);
   }

}
