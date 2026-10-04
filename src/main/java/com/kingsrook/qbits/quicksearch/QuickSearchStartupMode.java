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
 ** What happens when OpenSearch cannot be reached while the QBit starts.
 **
 ** FAIL_FAST - produce() and the runtime service throw, so the host does not
 **             boot with search silently broken. The default.
 ** DEGRADED  - the failure is logged; the host boots; indexing and search
 **             retry the connection on first use and report through the
 **             health check. Intended for demo profiles that may run without
 **             an OpenSearch cluster.
 *******************************************************************************/
public enum QuickSearchStartupMode
{
   FAIL_FAST,
   DEGRADED
}
