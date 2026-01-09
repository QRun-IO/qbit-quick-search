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
package com.kingsrook.qbits.quicksearch.model;


import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Tests for QuickSearchIndexRun entity.
 *******************************************************************************/
class QuickSearchIndexRunTest
{

   /***************************************************************************
    ** Test TABLE_NAME constant.
    ***************************************************************************/
   @Test
   void testTableNameConstant()
   {
      assertThat(QuickSearchIndexRun.TABLE_NAME).isEqualTo("quickSearchIndexRun");
   }



   /***************************************************************************
    ** Test all getters and fluent setters.
    ***************************************************************************/
   @Test
   void testGettersAndSetters()
   {
      Instant start = Instant.now();
      Instant end = start.plusSeconds(60);

      QuickSearchIndexRun run = new QuickSearchIndexRun()
         .withId(1)
         .withQuickSearchIndexId(100)
         .withRunType("FULL")
         .withStatus("COMPLETE")
         .withStartTime(start)
         .withEndTime(end)
         .withRecordsProcessed(1000)
         .withRecordsIndexed(950)
         .withErrorCount(5)
         .withErrorMessage("Some warnings");

      assertThat(run.getId()).isEqualTo(1);
      assertThat(run.getQuickSearchIndexId()).isEqualTo(100);
      assertThat(run.getRunType()).isEqualTo("FULL");
      assertThat(run.getStatus()).isEqualTo("COMPLETE");
      assertThat(run.getStartTime()).isEqualTo(start);
      assertThat(run.getEndTime()).isEqualTo(end);
      assertThat(run.getRecordsProcessed()).isEqualTo(1000);
      assertThat(run.getRecordsIndexed()).isEqualTo(950);
      assertThat(run.getErrorCount()).isEqualTo(5);
      assertThat(run.getErrorMessage()).isEqualTo("Some warnings");
   }



   /***************************************************************************
    ** Test standard setters.
    ***************************************************************************/
   @Test
   void testStandardSetters()
   {
      Instant start = Instant.now();
      Instant end = start.plusSeconds(30);

      QuickSearchIndexRun run = new QuickSearchIndexRun();
      run.setId(2);
      run.setQuickSearchIndexId(200);
      run.setRunType("BASEPULL");
      run.setStatus("RUNNING");
      run.setStartTime(start);
      run.setEndTime(end);
      run.setRecordsProcessed(500);
      run.setRecordsIndexed(500);
      run.setErrorCount(0);
      run.setErrorMessage(null);

      assertThat(run.getId()).isEqualTo(2);
      assertThat(run.getQuickSearchIndexId()).isEqualTo(200);
      assertThat(run.getRunType()).isEqualTo("BASEPULL");
      assertThat(run.getStatus()).isEqualTo("RUNNING");
      assertThat(run.getStartTime()).isEqualTo(start);
      assertThat(run.getEndTime()).isEqualTo(end);
      assertThat(run.getRecordsProcessed()).isEqualTo(500);
      assertThat(run.getRecordsIndexed()).isEqualTo(500);
      assertThat(run.getErrorCount()).isEqualTo(0);
      assertThat(run.getErrorMessage()).isNull();
   }



   /***************************************************************************
    ** Test fluent setters return this.
    ***************************************************************************/
   @Test
   void testFluentSetters_returnThis()
   {
      QuickSearchIndexRun run = new QuickSearchIndexRun();
      Instant now = Instant.now();

      assertThat(run.withId(1)).isSameAs(run);
      assertThat(run.withQuickSearchIndexId(1)).isSameAs(run);
      assertThat(run.withRunType("FULL")).isSameAs(run);
      assertThat(run.withStatus("RUNNING")).isSameAs(run);
      assertThat(run.withStartTime(now)).isSameAs(run);
      assertThat(run.withEndTime(now)).isSameAs(run);
      assertThat(run.withRecordsProcessed(0)).isSameAs(run);
      assertThat(run.withRecordsIndexed(0)).isSameAs(run);
      assertThat(run.withErrorCount(0)).isSameAs(run);
      assertThat(run.withErrorMessage(null)).isSameAs(run);
   }

}
