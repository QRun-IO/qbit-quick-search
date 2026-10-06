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


import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qbits.quicksearch.QuickSearchAuthMode;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchTlsConfig;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Unit tests for OpenSearchTransportFactory: secret and path references that
 ** resolve to nothing, and the hostname-verification warning.
 *******************************************************************************/
class OpenSearchTransportFactoryTest
{
   private static final String EMPTY_PROPERTY = "quickSearch.test.emptyValue";
   private static final String USER_PROPERTY  = "quickSearch.test.userValue";
   private static final String UNSET_ENV      = "${env.QUICK_SEARCH_TEST_UNSET_VARIABLE_15}";



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void afterEach()
   {
      System.clearProperty(EMPTY_PROPERTY);
      System.clearProperty(USER_PROPERTY);
      QLogger.deactivateCollectingLoggerForClass(OpenSearchTransportFactory.class);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QuickSearchQBitConfig baseConfig()
   {
      return (new QuickSearchQBitConfig()
         .withOpensearchUrl("https://localhost:9200")
         .withOpensearchIndexName("app-search"));
   }



   @Test
   void testBasic_emptyPasswordReference_namesFieldNotSecrets()
   {
      System.setProperty(USER_PROPERTY, "secret-user-value");
      System.setProperty(EMPTY_PROPERTY, "");

      QuickSearchQBitConfig config = baseConfig()
         .withAuthMode(QuickSearchAuthMode.BASIC)
         .withOpensearchUsername("${prop." + USER_PROPERTY + "}")
         .withOpensearchPassword("${prop." + EMPTY_PROPERTY + "}");

      assertThatThrownBy(() -> OpenSearchTransportFactory.build(config))
         .isInstanceOf(QException.class)
         .hasMessageContaining("opensearchPassword")
         .hasMessageContaining("${prop." + EMPTY_PROPERTY + "}")
         .hasMessageNotContaining("secret-user-value");
   }



   @Test
   void testBasic_blankOrUnsetReference_isTreatedAsMissing()
   {
      System.setProperty(EMPTY_PROPERTY, "   ");

      QuickSearchQBitConfig blankPassword = baseConfig()
         .withAuthMode(QuickSearchAuthMode.BASIC)
         .withOpensearchUsername("admin")
         .withOpensearchPassword("${prop." + EMPTY_PROPERTY + "}");
      assertThatThrownBy(() -> OpenSearchTransportFactory.build(blankPassword))
         .isInstanceOf(QException.class)
         .hasMessageContaining("opensearchPassword");

      QuickSearchQBitConfig unsetUsername = baseConfig()
         .withAuthMode(QuickSearchAuthMode.BASIC)
         .withOpensearchUsername(UNSET_ENV)
         .withOpensearchPassword("not-in-the-message");
      assertThatThrownBy(() -> OpenSearchTransportFactory.build(unsetUsername))
         .isInstanceOf(QException.class)
         .hasMessageContaining("opensearchUsername")
         .hasMessageContaining(UNSET_ENV)
         .hasMessageNotContaining("not-in-the-message");
   }



   @Test
   void testTls_unresolvedPaths_nameTheField()
   {
      System.setProperty(EMPTY_PROPERTY, "");

      assertPathError(new QuickSearchTlsConfig().withCaCertificatePath(UNSET_ENV), "tls.caCertificatePath", UNSET_ENV);
      assertPathError(new QuickSearchTlsConfig().withTrustStorePath(UNSET_ENV), "tls.trustStorePath", UNSET_ENV);
      assertPathError(new QuickSearchTlsConfig().withKeyStorePath("${prop." + EMPTY_PROPERTY + "}"), "tls.keyStorePath", "${prop." + EMPTY_PROPERTY + "}");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void assertPathError(QuickSearchTlsConfig tls, String fieldName, String reference)
   {
      assertThatThrownBy(() -> OpenSearchTransportFactory.build(baseConfig().withTls(tls)))
         .isInstanceOf(QException.class)
         .hasMessageContaining(fieldName)
         .hasMessageContaining(reference)
         .hasMessageNotContaining(": null");
   }



   @Test
   void testHostnameVerificationDisabled_logsWarning()
   {
      QCollectingLogger collectingLogger = QLogger.activateCollectingLoggerForClass(OpenSearchTransportFactory.class);

      assertThat(OpenSearchTransportFactory.buildHostnameVerifier(new QuickSearchTlsConfig().withHostnameVerification(false))).isSameAs(NoopHostnameVerifier.INSTANCE);
      assertThat(collectingLogger.getCollectedMessages()).anyMatch(m -> Level.WARN.equals(m.getLevel()) && m.getMessage().contains("tls.hostnameVerification"));

      collectingLogger.clear();
      assertThat(OpenSearchTransportFactory.buildHostnameVerifier(new QuickSearchTlsConfig())).isNull();
      assertThat(OpenSearchTransportFactory.buildHostnameVerifier(null)).isNull();
      assertThat(collectingLogger.getCollectedMessages()).isEmpty();
   }

}
