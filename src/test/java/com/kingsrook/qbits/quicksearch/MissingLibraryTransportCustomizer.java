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

package com.kingsrook.qbits.quicksearch;


import com.kingsrook.qbits.quicksearch.opensearch.OpenSearchTransportCustomizer;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;


/*******************************************************************************
 ** Test transport customizer that fails linkage while the OpenSearch client is
 ** built, as a missing optional library (the AWS SDK for AWS_SIGV4) does.
 *******************************************************************************/
public class MissingLibraryTransportCustomizer implements OpenSearchTransportCustomizer
{
   public static final String MISSING_CLASS = "software/amazon/awssdk/http/SdkHttpClient";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public ApacheHttpClient5TransportBuilder customizeTransport(ApacheHttpClient5TransportBuilder builder)
   {
      throw (new NoClassDefFoundError(MISSING_CLASS));
   }

}
