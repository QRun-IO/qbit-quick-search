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


/*******************************************************************************
 ** How the QBit authenticates to OpenSearch.
 **
 ** NONE      - no credentials (local development, security plugin disabled).
 ** BASIC     - HTTP basic authentication (security plugin internal users, or
 **             Amazon OpenSearch Service fine-grained access control with an
 **             internal-database master user).
 ** AWS_SIGV4 - AWS Signature Version 4 request signing with the default AWS
 **             credential chain (Amazon OpenSearch Service domains that use
 **             IAM). Requires the optional AWS SDK v2 dependencies.
 **
 ** Bearer tokens, API keys and client certificates are added through an
 ** {@link com.kingsrook.qbits.quicksearch.opensearch.OpenSearchTransportCustomizer}.
 *******************************************************************************/
public enum QuickSearchAuthMode
{
   NONE,
   BASIC,
   AWS_SIGV4
}
